"""脚本执行入口：供 Kotlin 侧 PythonEngine 调用。

职责：注入运行时依赖路径、重定向输出流、切换工作目录、
逐行检查停止标志（settrace），并统一格式化异常退出码。
"""

import os
import sys
import traceback
import runpy


class _OutputWriter:
    """把 print / stderr 写调用转发为 Java 侧流式回调。

    上层 write 可能按任意 chunk 切分（print 的多参数会分开写入），
    这里按行缓冲：凑满一行才回调，避免一行输出被拆成多条控制台记录。
    """

    def __init__(self, listener, run_id, stream):
        self._listener = listener
        self._run_id = run_id
        self._stream = stream
        self._buf = ""

    def write(self, text):
        if not text:
            return
        self._buf += text
        while "\n" in self._buf:
            line, self._buf = self._buf.split("\n", 1)
            self._listener.onOutput(self._run_id, self._stream, line)

    def flush(self):
        if self._buf:
            self._listener.onOutput(self._run_id, self._stream, self._buf)
            self._buf = ""

    def isatty(self):
        return False


def run_script(entry_path, work_dir, deps_dir, args, listener, run_id, out_enum, err_enum, stop_flag):
    """执行脚本并返回退出码：0 成功、130 被停止、其他为失败"""
    if deps_dir:
        sys.path.insert(0, deps_dir)
    os.chdir(work_dir)
    sys.argv = [entry_path] + list(args)

    saved_out, saved_err = sys.stdout, sys.stderr
    sys.stdout = _OutputWriter(listener, run_id, out_enum)
    sys.stderr = _OutputWriter(listener, run_id, err_enum)

    def _trace(frame, event, arg):
        if stop_flag.isStopped():
            raise SystemExit(130)
        return _trace

    sys.settrace(_trace)
    try:
        runpy.run_path(entry_path, run_name="__main__")
        return 0
    except SystemExit as e:
        code = e.code
        if stop_flag.isStopped() or code == 130:
            return 130
        if code in (None, 0):
            return 0
        if isinstance(code, int):
            return code
        sys.stderr.write(str(code) + "\n")
        return 1
    except BaseException:
        sys.stderr.write(traceback.format_exc())
        return 1
    finally:
        sys.settrace(None)
        sys.stdout.flush()
        sys.stderr.flush()
        sys.stdout, sys.stderr = saved_out, saved_err
