"""Diagnose the real MC26.3 SDL/GL requirements. Never relax the game's requirements."""
import ctypes,hashlib,json,os,subprocess,urllib.request,zipfile
from pathlib import Path

out=Path(os.environ['FTE_PHASE5_EVIDENCE'])/'graphics-26.3';out.mkdir(parents=True,exist_ok=True)
for name,command in [('glxinfo',['glxinfo','-t']),('vulkaninfo',['vulkaninfo','--summary'])]:
    with (out/(name+'.txt')).open('wb') as log:
        try:result=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=30)
        except subprocess.TimeoutExpired:log.write(b'DIAGNOSTIC_TIMEOUT\n')
url='https://libraries.minecraft.net/org/lwjgl/lwjgl-sdl/3.4.3/lwjgl-sdl-3.4.3-natives-linux.jar'
archive=out/'lwjgl-sdl-3.4.3-natives-linux.jar'
with urllib.request.urlopen(url,timeout=60) as response:archive.write_bytes(response.read())
# SHA-1 is the exact immutable artifact digest in Mojang's 26.3 version manifest.
assert hashlib.sha1(archive.read_bytes()).hexdigest()=='6ed1b80b7c211a491612c70e43f27ba1f42cccd0'
with zipfile.ZipFile(archive) as source:
    name=next(n for n in source.namelist() if n.endswith('/libSDL3.so'))
    library=out/'libSDL3.so';library.write_bytes(source.read(name))
os.environ.update(SDL_VIDEODRIVER='x11',SDL_VIDEO_DRIVER='x11',XDG_SESSION_TYPE='x11')
os.environ.pop('WAYLAND_DISPLAY',None)
sdl=ctypes.CDLL(str(library.resolve()))
sdl.SDL_Init.argtypes=[ctypes.c_uint];sdl.SDL_Init.restype=ctypes.c_bool
sdl.SDL_GL_SetAttribute.argtypes=[ctypes.c_int,ctypes.c_int];sdl.SDL_GL_SetAttribute.restype=ctypes.c_bool
sdl.SDL_CreateWindow.argtypes=[ctypes.c_char_p,ctypes.c_int,ctypes.c_int,ctypes.c_uint64];sdl.SDL_CreateWindow.restype=ctypes.c_void_p
sdl.SDL_GL_CreateContext.argtypes=[ctypes.c_void_p];sdl.SDL_GL_CreateContext.restype=ctypes.c_void_p
sdl.SDL_GL_DestroyContext.argtypes=[ctypes.c_void_p];sdl.SDL_GL_DestroyContext.restype=ctypes.c_bool
sdl.SDL_DestroyWindow.argtypes=[ctypes.c_void_p]
sdl.SDL_GetError.restype=ctypes.c_char_p
checks={'SDL_Init_video':bool(sdl.SDL_Init(0x20))};values=[]
for srgb in [1,0]:
    # Same attributes and hidden utility window flags as MC's GlBackend.createWindow().
    for key,value in [(17,3),(18,3),(20,1),(19,2),(22,srgb)]:sdl.SDL_GL_SetAttribute(key,value)
    window=sdl.SDL_CreateWindow(b'MC26.3 graphics diagnostic',320,480,2147614728|2)
    context=sdl.SDL_GL_CreateContext(window) if window else None
    values.append({'srgb_required':srgb,'window':bool(window),'context':bool(context),'error':(sdl.SDL_GetError() or b'').decode(errors='replace')})
    if context:sdl.SDL_GL_DestroyContext(context)
    if window:sdl.SDL_DestroyWindow(window)
sdl.SDL_Quit()
value={'status':'DIAGNOSTIC','checks':checks,'GL3_3_core_results':values,'artifact_url':url,'artifact_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
       'scope':'srgb=0 is a negative diagnostic control only; no game setting/SDK/JVM/driver override. Actual production runtime remains mandatory.'}
(out/'result.json').write_text(json.dumps(value,indent=2)+'\n');print('GRAPHICS_DIAGNOSTIC',json.dumps(value))
