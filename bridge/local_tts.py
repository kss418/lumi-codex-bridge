"""Optional local Lumi TTS. Downloads occur ONLY with --install."""
import argparse, base64, contextlib, csv, hashlib, io, json, os, inspect
from pathlib import Path
import shutil, subprocess, sys, urllib.request, wave, threading

RUNTIMES = {
 'legacy': ('https://huggingface.co/lj1995/GPT-SoVITS-windows-package/resolve/main/GPT-SoVITS-v2-240821.7z?download=true','9d9ba79de6aca0cf28a3635ccb1dbbb08b6aef362c4352e32fad99bb49e3000a'),
 'rtx50': ('https://huggingface.co/lj1995/GPT-SoVITS-windows-package/resolve/main/GPT-SoVITS-v2pro-20250604-nvidia50.7z?download=true','97b4edcd451c42357db7e26e6c1c877ca5d85144fe97beaff6d7005d35bee008')}
WEIGHTS=('https://github.com/snowtie/LUMI-to-GPT/releases/download/v0.9.0/GPT_weights_v2.7z','4a0ff7071c3d0d4c56a48016d8bc66ca5c8c626d599c0e71300f0de3afa14e79')
FLAGS=0x08000000 if os.name=='nt' else 0

def atomic_json(path, value):
 path.parent.mkdir(parents=True,exist_ok=True)
 temp=path.with_suffix('.tmp'); temp.write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8'); temp.replace(path)

def installed(root):
 try:
  config=json.loads((root/'installed.json').read_text(encoding='utf-8'))
  return config if all(Path(config[k]).is_file() for k in ['python','gpt','sovits','reference']) else None
 except (OSError,ValueError,KeyError):return None

def progress(phase, path, downloaded=0, total=None):
 print(json.dumps({'event':'install_progress','phase':phase,'file':path.name,'bytes':downloaded,'total':total}),flush=True)

def content_length(url):
 try:
  with urllib.request.urlopen(urllib.request.Request(url,method='HEAD'),timeout=10) as response:
   value=int(response.headers.get('Content-Length','0'))
   return value if value>0 else None
 except (OSError,ValueError):return None

def fetch(url,path,sha=None):
 path.parent.mkdir(parents=True,exist_ok=True)
 if path.exists() and sha:
  progress('verify',path)
  with path.open('rb') as f:
   if hashlib.file_digest(f,'sha256').hexdigest()==sha:
    progress('cached',path,path.stat().st_size,path.stat().st_size)
    return
 part=path.with_suffix(path.suffix+'.part')
 progress('prepare',path)
 total=content_length(url)
 stopped=threading.Event()
 def report():
  try:size=part.stat().st_size
  except FileNotFoundError:size=0
  progress('download',path,size,total)
 def monitor():
  while not stopped.wait(0.5):report()
 report()
 watcher=threading.Thread(target=monitor,daemon=True);watcher.start()
 try:
  subprocess.run(['curl.exe','-L','--fail','--silent','--show-error','--retry','3','-C','-','-o',str(part),url],check=True,creationflags=FLAGS)
 finally:
  stopped.set();watcher.join()
 report()
 if sha:
  progress('verify',path,part.stat().st_size,total)
  with part.open('rb') as f:
   if hashlib.file_digest(f,'sha256').hexdigest()!=sha:raise RuntimeError('다운로드 파일 검증에 실패했습니다: '+path.name)
 part.replace(path)
 progress('download_done',path,path.stat().st_size,total)


def reference_voice(lumi_home):
 base=lumi_home/'app/voice/Lumi'
 for index in base.rglob('index.tsv'):
  with index.open(encoding='utf-8-sig',newline='') as f:
   for row in csv.DictReader(f,delimiter='\t'):
    audio=(index.parent/row.get('file','')).resolve()
    if not audio.is_relative_to(base.resolve()):continue
    try:duration=int(row.get('ms','0'))
    except ValueError:continue
    if audio.is_file() and 3000<=duration<=10000 and row.get('text','').strip():return audio,row['text'].strip()
 raise RuntimeError('참조 음성이 필요합니다. 한국어 루미 보이스팩을 먼저 설치해 주세요.')

def install(root,lumi_home):
 if installed(root):print('로컬 TTS가 이미 설치되어 있습니다.',flush=True);return
 reference,text=reference_voice(lumi_home)
 key='legacy'
 try:
  result=subprocess.run(['nvidia-smi','--query-gpu=compute_cap','--format=csv,noheader,nounits'],capture_output=True,text=True,timeout=5,creationflags=FLAGS)
  if any(float(line.strip())>=10 for line in result.stdout.splitlines()):key='rtx50'
 except (OSError,ValueError,subprocess.TimeoutExpired):pass
 if shutil.disk_usage(root.parent if root.parent.exists() else Path.home()).free<30*1024**3:raise RuntimeError('설치하려면 최소 30GiB의 여유 공간이 필요합니다.')
 downloads=root/'downloads'; downloads.mkdir(parents=True,exist_ok=True)
 # Prevent two settings windows from installing over each other.
 lock=root/'install.lock'
 lock_handle=lock.open('a+b')
 if lock_handle.tell()==0:lock_handle.write(b'0');lock_handle.flush()
 lock_handle.seek(0)
 import msvcrt
 try:msvcrt.locking(lock_handle.fileno(),msvcrt.LK_NBLCK,1)
 except OSError:
  lock_handle.close();raise RuntimeError('다른 TTS 설치가 진행 중입니다.')
 try:
  print('GPT-SoVITS 실행 환경을 다운로드합니다. 약 5.7GB이며 RTX 50 계열은 약 8.8GB입니다.',flush=True)
  fetch(*[RUNTIMES[key][0],downloads/(key+'.7z'),RUNTIMES[key][1]])
  print('루미 음성 모델을 다운로드합니다. 약 420MB입니다.',flush=True)
  fetch(WEIGHTS[0],downloads/'lumi.7z',WEIGHTS[1])
  fetch('https://github.com/snowtie/LUMI-to-GPT/releases/download/v0.9.0/VOICE_MODEL_NOTICE.txt',root/'VOICE_MODEL_NOTICE.txt')
  seven=downloads/'7zr.exe'
  if not seven.exists():fetch('https://www.7-zip.org/a/7zr.exe',seven)
  for archive,directory in [(downloads/(key+'.7z'),root/'runtime'),(downloads/'lumi.7z',root/'models')]:
   progress('extract',archive)
   subprocess.run([str(seven),'x',str(archive),'-o'+str(directory),'-y','-bso0','-bsp0'],check=True,creationflags=FLAGS)
  api=next((root/'runtime').rglob('api_v2.py'))
  runtime=api.parent
  python=runtime/'runtime/python.exe'
  if not python.is_file():raise RuntimeError('압축 해제한 실행 환경에서 runtime/python.exe를 찾지 못했습니다.')
  gpt=next((root/'models').rglob('LUMI-e10.ckpt'))
  sovits=next((root/'models').rglob('LUMI_e8_s880.pth'))
  shutil.copy2(reference,root/'reference.wav')
  atomic_json(root/'installed.json',{'version':1,'runtime':str(runtime),'python':str(python),'gpt':str(gpt),'sovits':str(sovits),'reference':str(root/'reference.wav'),'prompt_text':text})
  print('설치가 완료되었습니다. 설정에서 TTS 켜기를 선택하고 저장해 주세요.',flush=True)
 finally:
  lock_handle.seek(0);msvcrt.locking(lock_handle.fileno(),msvcrt.LK_UNLCK,1);lock_handle.close()

def configure_frontend(engine):
 """Legacy v2 omits version in BOTH target preprocessing and reference text."""
 from functools import wraps
 frontend = engine.text_preprocessor
 def uses_korean(text):
  return any('가' <= char <= '힣' for char in text)
 def wrap(original, reference):
  signature = inspect.signature(original)
  if 'version' not in signature.parameters:
   return original
  @wraps(original)
  def converted(*args, **kwargs):
   bound = signature.bind_partial(*args, **kwargs)
   if 'version' not in bound.arguments:
    kwargs['version'] = engine.configs.version
   result = original(*args, **kwargs)
   text = bound.arguments.get('text', '')
   normalized = result[2] if reference else ''.join(item.get('norm_text', '') for item in result)
   if uses_korean(text) and not uses_korean(normalized):
    raise RuntimeError('한국어 참조/목표 대사 전처리에 실패했습니다. 잘못된 음성 생성을 중단합니다.')
   return result
  return converted
 for name, reference in [('preprocess', False), ('segment_and_extract_feature_for_text', True)]:
  original = getattr(frontend, name, None)
  if original is not None:
   setattr(frontend, name, wrap(original, reference))


def serve(root,device):
 config=installed(root)
 if not config:raise RuntimeError('로컬 TTS가 설치되지 않았습니다. 설정에서 로컬 TTS 설치 버튼을 눌러 주세요.')
 # This mode is started with the optional runtime's Python, never the bridge venv.
 os.chdir(config['runtime']);sys.path[:0]=[config['runtime'],str(Path(config['runtime'])/'GPT_SoVITS')]
 os.environ['HF_HUB_OFFLINE']='1';os.environ['TRANSFORMERS_OFFLINE']='1'
 protocol=sys.stdout
 with contextlib.redirect_stdout(sys.stderr):
  import torch
  from GPT_SoVITS.TTS_infer_pack.TTS import TTS,TTS_Config
  use_cuda=device=='cuda' or (device=='auto' and torch.cuda.is_available())
  if use_cuda and not torch.cuda.is_available():raise RuntimeError('CUDA를 사용할 수 없습니다. 실행 장치를 CPU로 선택해 주세요.')
  settings={'version':'v2','custom':{'version':'v2','device':'cuda' if use_cuda else 'cpu','is_half':use_cuda,'t2s_weights_path':config['gpt'],'vits_weights_path':config['sovits'],'bert_base_path':'GPT_SoVITS/pretrained_models/chinese-roberta-wwm-ext-large','cnhuhbert_base_path':'GPT_SoVITS/pretrained_models/chinese-hubert-base'}}
  engine=TTS(TTS_Config(settings))
  configure_frontend(engine)
 print(json.dumps({'event':'ready'}),file=protocol,flush=True)
 for line in sys.stdin:
  request_id=None
  try:
   req=json.loads(line);request_id=req['id'];text=req['text']
   if not isinstance(text,str) or not text.strip() or len(text)>2000:raise ValueError('읽을 내용은 1~2000자여야 합니다.')
   with contextlib.redirect_stdout(sys.stderr):
    rate,data=next(engine.run({'text':text,'text_lang':'ko','ref_audio_path':config['reference'],'prompt_text':config['prompt_text'],'prompt_lang':'ko','text_split_method':'cut5','batch_size':1,'return_fragment':False,'speed_factor':1.0,'seed':-1}))
   output=io.BytesIO()
   with wave.open(output,'wb') as wav:
    wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(int(rate));wav.writeframes(data.astype('<i2').tobytes())
   print(json.dumps({'id':request_id,'wav':base64.b64encode(output.getvalue()).decode()}),file=protocol,flush=True)
  except Exception as error:print(json.dumps({'id':request_id,'error':str(error)},ensure_ascii=False),file=protocol,flush=True)

def error_message(error):
 if isinstance(error, subprocess.CalledProcessError):
  command = Path(str(error.cmd[0])).name if isinstance(error.cmd, (list, tuple)) else str(error.cmd)
  action = '다운로드' if command.lower() == 'curl.exe' else '외부 도구 실행'
  return f'{action}에 실패했습니다. 종료 코드: {error.returncode}. 위의 상세 로그를 확인해 주세요.'
 if isinstance(error, StopIteration):
  return '설치 파일에서 필요한 실행 환경 또는 음성 모델을 찾지 못했습니다.'
 return str(error)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True)
 mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--install',action='store_true');mode.add_argument('--status',action='store_true');mode.add_argument('--serve',action='store_true')
 parser.add_argument('--lumi-home',type=Path);parser.add_argument('--device',choices=['auto','cpu','cuda'],default='auto');args=parser.parse_args()
 try:
  if args.status:print(json.dumps({'installed':installed(args.root) is not None}));return 0
  if args.install:
   if args.lumi_home is None:raise ValueError('꼬미 설치 경로(--lumi-home)가 필요합니다.')
   install(args.root,args.lumi_home)
  else:serve(args.root,args.device)
  return 0
 except Exception as error:print(json.dumps({'event':'error','message':error_message(error)},ensure_ascii=False),flush=True);return 1
if __name__=='__main__':raise SystemExit(main())
