import contextlib,io,json,sys,tempfile,unittest,hashlib
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'bridge'))
import local_tts

class LocalTtsTests(unittest.TestCase):
 def test_status_never_installs_or_creates_files(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d)/'absent';output=io.StringIO()
   with patch.object(sys,'argv',['local_tts','--root',str(root),'--status']),patch.object(local_tts,'fetch') as fetch,contextlib.redirect_stdout(output):
    self.assertEqual(local_tts.main(),0)
   self.assertEqual(json.loads(output.getvalue()),{'installed':False});self.assertFalse(root.exists());fetch.assert_not_called()
 def test_missing_installation_does_not_load_torch_or_download(self):
  with tempfile.TemporaryDirectory() as d,patch.object(local_tts,'fetch') as fetch:
   with self.assertRaisesRegex(RuntimeError,'설치되지 않았습니다'):local_tts.serve(Path(d),'auto')
   fetch.assert_not_called();self.assertNotIn('torch',sys.modules)
 def test_installed_requires_all_files(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d); paths={k:str(root/k) for k in ['python','gpt','sovits','reference']}
   local_tts.atomic_json(root/'installed.json',paths)
   self.assertIsNone(local_tts.installed(root))
   for p in paths.values():Path(p).write_bytes(b'test')
   self.assertEqual(local_tts.installed(root),paths)
 def test_reference_voice_selection(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);voice=root/'app/voice/Lumi/test';voice.mkdir(parents=True)
   (voice/'ref.wav').write_bytes(b'test')
   (voice/'index.tsv').write_text('id\tsource\tms\tfile\ttext\n1\ttest\t4000\tref.wav\t안녕하세요\n',encoding='utf-8')
   path,text=local_tts.reference_voice(root);self.assertEqual(path.name,'ref.wav');self.assertEqual(text,'안녕하세요')
 def test_download_checksum_failure_not_marked_complete(self):
  with tempfile.TemporaryDirectory() as d:
   target=Path(d)/'runtime.7z'
   def fake_run(args,**kwargs):Path(args[args.index('-o')+1]).write_bytes(b'bad')
   with patch.object(local_tts,'content_length',return_value=3),patch.object(local_tts.subprocess,'run',side_effect=fake_run):
    with self.assertRaisesRegex(RuntimeError,'검증에 실패'):local_tts.fetch('https://example.test',target,hashlib.sha256(b'good').hexdigest())
   self.assertFalse(target.exists())
 def test_progress_reports_downloaded_bytes_and_total(self):
  with tempfile.TemporaryDirectory() as d:
   target=Path(d)/'file.bin';output=io.StringIO()
   def fake_run(args,**kwargs):Path(args[args.index('-o')+1]).write_bytes(b'hello')
   with patch.object(local_tts,'content_length',return_value=5),patch.object(local_tts.subprocess,'run',side_effect=fake_run),contextlib.redirect_stdout(output):
    local_tts.fetch('https://example.test',target)
   events=[json.loads(line) for line in output.getvalue().splitlines()]
   self.assertTrue(any(e['phase']=='download' and e['bytes']==5 and e['total']==5 for e in events))
   self.assertEqual(events[-1]['phase'],'download_done')
 def test_unknown_total_still_reports_bytes(self):
  with tempfile.TemporaryDirectory() as d:
   target=Path(d)/'file.bin';output=io.StringIO()
   def fake_run(args,**kwargs):Path(args[args.index('-o')+1]).write_bytes(b'hi')
   with patch.object(local_tts,'content_length',return_value=None),patch.object(local_tts.subprocess,'run',side_effect=fake_run),contextlib.redirect_stdout(output):
    local_tts.fetch('https://example.test',target)
   events=[json.loads(line) for line in output.getvalue().splitlines()]
   self.assertEqual(events[-1]['bytes'],2);self.assertIsNone(events[-1]['total'])
 def test_legacy_frontend_gets_model_version(self):
  from types import SimpleNamespace
  versions=[]
  def preprocess(text,lang,split,version='v1'):
   versions.append(version)
   return [{'norm_text':text if version=='v2' else ' '}]
  frontend=SimpleNamespace(preprocess=preprocess)
  engine=SimpleNamespace(text_preprocessor=frontend,configs=SimpleNamespace(version='v2'))
  local_tts.configure_frontend(engine)
  self.assertEqual(frontend.preprocess('안녕하세요','ko','cut5')[0]['norm_text'],'안녕하세요')
  self.assertEqual(versions,['v2'])
 def test_dropped_korean_is_not_synthesized(self):
  from types import SimpleNamespace
  def preprocess(text,lang,split,version='v1'):return [{'norm_text':' '}]
  frontend=SimpleNamespace(preprocess=preprocess)
  local_tts.configure_frontend(SimpleNamespace(text_preprocessor=frontend,configs=SimpleNamespace(version='v2')))
  with self.assertRaisesRegex(RuntimeError,'한국어 참조/목표 대사 전처리'):frontend.preprocess('안녕하세요','ko','cut5')
 def test_new_frontend_without_version_is_unchanged(self):
  from types import SimpleNamespace
  def preprocess(text,lang,split):return [{'norm_text':text}]
  frontend=SimpleNamespace(preprocess=preprocess)
  local_tts.configure_frontend(SimpleNamespace(text_preprocessor=frontend,configs=SimpleNamespace(version='v2')))
  self.assertIs(frontend.preprocess,preprocess)
 def test_reference_text_also_uses_v2(self):
  from types import SimpleNamespace
  seen=[]
  def segment(text,language,version='v1'):
   seen.append(version);return [1],None,text if version=='v2' else ' '
  frontend=SimpleNamespace(segment_and_extract_feature_for_text=segment)
  local_tts.configure_frontend(SimpleNamespace(text_preprocessor=frontend,configs=SimpleNamespace(version='v2')))
  self.assertEqual(frontend.segment_and_extract_feature_for_text('루미예요','ko')[2],'루미예요')
  frontend.segment_and_extract_feature_for_text('hello','en','v1')
  self.assertEqual(seen,['v2','v1'])
 def test_broken_reference_is_rejected(self):
  from types import SimpleNamespace
  def segment(text,language,version='v1'):return [1],None,' '
  frontend=SimpleNamespace(segment_and_extract_feature_for_text=segment)
  local_tts.configure_frontend(SimpleNamespace(text_preprocessor=frontend,configs=SimpleNamespace(version='v2')))
  with self.assertRaisesRegex(RuntimeError,'참조/목표'):frontend.segment_and_extract_feature_for_text('루미예요','ko')
if __name__=='__main__':unittest.main()
