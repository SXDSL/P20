# -*- mode: python ; coding: utf-8 -*-
# Build:  python -m PyInstaller build.spec --noconfirm   (run from this folder)

from PyInstaller.utils.hooks import collect_data_files

a = Analysis(
    ['lan_server.py'],
    pathex=[],
    binaries=[],
    datas=[
        ('hentai roulette.html', '.'),
    ] + collect_data_files('webview') + collect_data_files('pythonnet'),
    hiddenimports=[
        'clr',
        'pythonnet',
        'bottle',
        'proxy_tools',
        'webview.platforms.winforms',
        'webview.platforms.edgechromium',
        'webview.platforms.win32',
        'webview.platforms.mshtml',
    ],
    hookspath=[],
    excludes=[],
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='P20-Roulette',
    debug=False,
    strip=False,
    upx=False,
    console=False,
    icon='app.ico',
    disable_windowed_traceback=False,
)
