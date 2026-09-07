import sys, unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'bridge'))
from codex_client import CodexClient
from stdio_bridge import StdioSession, ProtocolError

class PersonaTests(unittest.TestCase):
 def test_persona_is_instruction_not_user_message(self):
  c=CodexClient(); c.request=Mock(return_value={'thread':{'id':'t'},'model':'m'})
  c.start_thread(persona='고양이 말투로 대답해.')
  instructions=c.request.call_args.args[1]['developerInstructions']
  self.assertIn('고양이 말투',instructions)
  self.assertNotIn('Use polite language',instructions)
 def test_default_tone_without_persona(self):
  c=CodexClient(); c.request=Mock(return_value={'thread':{'id':'t'},'model':'m'})
  c.start_thread(); self.assertIn('Use polite language',c.request.call_args.args[1]['developerInstructions'])
 def test_same_persona_reuses_thread_changed_persona_starts_new_thread(self):
  c=Mock();c.start_thread.side_effect=['a','b','c'];c.send_message.return_value='ok'
  session=StdioSession(c)
  session.dispatch('chat',{'text':'hi','persona':'cat'})
  session.dispatch('chat',{'text':'again'})
  self.assertEqual(c.start_thread.call_count,1)
  session.dispatch('chat',{'text':'hi','persona':'dog'})
  self.assertEqual(c.start_thread.call_args.kwargs['persona'],'dog')
  session.dispatch('chat',{'text':'hi','persona':''})
  self.assertEqual(c.start_thread.call_count,3)
  self.assertNotIn('persona',c.start_thread.call_args.kwargs)
  self.assertEqual([x.args[0] for x in c.send_message.call_args_list],['a','a','b','c'])
 def test_invalid_persona_does_not_start_thread(self):
  c=Mock(); session=StdioSession(c)
  for value in [None,{},'x'*20001]:
   with self.assertRaises(ProtocolError): session.dispatch('chat',{'text':'hi','persona':value})
  c.start_thread.assert_not_called()

if __name__=='__main__': unittest.main()
