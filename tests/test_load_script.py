import pathlib, py_compile
ROOT = pathlib.Path(__file__).resolve().parents[1]
py_compile.compile(str(ROOT / 'tests' / 'load' / 'api_load.py'), doraise=True)
print('load probe syntax: PASS')
