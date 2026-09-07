import sys,base64,unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'bridge'))
from codex_client import CodexClient,validate_image
from stdio_bridge import StdioSession
IMAGE='data:image/png;base64,'+base64.b64encode(b'\x89PNG\r\n\x1a\nfixture').decode()
class ImageTests(unittest.TestCase):
 def test_image_forwarded_as_image_input(self):
  c=CodexClient();c._thread_models={'t':'m'};c._models=[{'id':'m','model':'m','inputModalities':['text','image']}]
  c.request=Mock(return_value={'turn':{'id':'u'}})
  c._events.append({'method':'turn/completed','params':{'threadId':'t','turn':{'id':'u','status':'completed','items':[]}}})
  c.send_message('t','describe',image=IMAGE)
  self.assertEqual(c.request.call_args.args[1]['input'][1],{'type':'image','url':IMAGE})
 def test_text_only_model_rejected_before_turn(self):
  c=CodexClient();c._thread_models={'t':'m'};c._models=[{'id':'m','model':'m','inputModalities':['text']}];c.request=Mock()
  with self.assertRaisesRegex(ValueError,'does not support images'):c.send_message('t','describe',image=IMAGE)
  c.request.assert_not_called()
 def test_invalid_images_rejected(self):
  for image in ['https://example.com/image.png','data:image/png;base64,notbase64',123,'data:image/png;base64,YWJj']:
   with self.assertRaises(ValueError):validate_image(image)
 def test_protocol_forwards_image(self):
  c=Mock();c.start_thread.return_value='t';c.send_message.return_value='blue'
  StdioSession(c).dispatch('chat',{'text':'describe','image':IMAGE})
  self.assertEqual(c.send_message.call_args.kwargs['image'],IMAGE)
if __name__=='__main__':unittest.main()
