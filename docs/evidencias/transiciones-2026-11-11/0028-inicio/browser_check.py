"""Comprueba selectores/render de un grupo aislado en Chromium headless por CDP.

Uso: python3 browser_check.py /ruta/group-NN PUERTO
Chromium debe estar abierto en el visor del grupo con remote-debugging-port.
"""
import base64
import json
import sys
import urllib.request
from pathlib import Path
from websockets.sync.client import connect

out = Path(sys.argv[1])
port = int(sys.argv[2])
windows = json.loads((out/'verification.json').read_text())[0]['windows']
pages = json.load(urllib.request.urlopen(f'http://127.0.0.1:{port}/json'))
page = next(p for p in pages if p['type']=='page' and out.name in p['url'])
with connect(page['webSocketDebuggerUrl']) as ws:
    counter = 0
    def cmd(method, params=None):
        global counter
        counter += 1
        ws.send(json.dumps({'id':counter,'method':method,'params':params or {}}))
        while True:
            result = json.loads(ws.recv(timeout=30))
            if result.get('id')==counter:
                assert 'error' not in result, result
                return result
    def ev(expression):
        result = cmd('Runtime.evaluate', {'expression':expression,'awaitPromise':True,'returnByValue':True})
        assert 'exceptionDetails' not in result.get('result',{}), result
        return result['result']['result'].get('value')
    try:
        cmd('Emulation.setDeviceMetricsOverride',{'width':1400,'height':950,'deviceScaleFactor':1,'mobile':False})
        checks=[]
        for avatar in ('juan','pauline'):
            if avatar=='pauline': ev('document.getElementById("avatar").click()')
            ev('new Promise((resolve,reject)=>{let n=0;const id=setInterval(()=>{if(window.previewReady==='+json.dumps(avatar)+'){clearInterval(id);resolve(window.previewReady);}else if(++n>200){clearInterval(id);reject("timeout");}},100);})')
            for sign in windows:
                for variant in ('afirmativo','pregunta','negacion','orden'):
                    expression='document.getElementById("sign").value='+json.dumps(sign)+';document.getElementById("variant").value='+json.dumps(variant)+';document.getElementById("sign").dispatchEvent(new Event("change"));window.previewTime=.75;new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve(window.previewSelection))))'
                    selected=ev(expression)
                    assert selected==f'{sign}_{variant}', selected
                    checks.append({'avatar':avatar,'clip':selected,'selected_and_rendered':True})
                    if variant=='afirmativo':
                        shot=cmd('Page.captureScreenshot')
                        (out/f'{avatar}-{sign}.png').write_bytes(base64.b64decode(shot['result']['data']))
        (out/'browser-checks.json').write_text(json.dumps(checks,indent=2))
        print(f'{len(checks)} selecciones y render comprobados, ambos avatares')
    finally:
        cmd('Browser.close')
