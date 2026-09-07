import io,json,sys,threading,unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'bridge'))
from codex_client import CodexClient,GenerationCancelled
from stdio_bridge import StdioSession,serve_lines

class CancelTests(unittest.TestCase):
 def test_interrupt_targets_current_turn_and_preserves_client(self):
  c=CodexClient(); token=threading.Event()
  def request(method,params):
   if method=='turn/start': token.set(); return {'turn':{'id':'turn-1'}}
   self.assertEqual(method,'turn/interrupt');self.assertEqual(params,{'threadId':'thread-1','turnId':'turn-1'})
   c._events.append({'method':'turn/completed','params':{'threadId':'thread-1','turn':{'id':'turn-1','status':'interrupted'}}})
   return {}
  c.request=Mock(side_effect=request);c.close=Mock()
  with self.assertRaises(GenerationCancelled): c.send_message('thread-1','hello',cancel_event=token)
  c.close.assert_not_called()
 def test_pre_cancelled_request_does_not_generate(self):
  c=CodexClient();c.request=Mock();token=threading.Event();token.set()
  with self.assertRaises(GenerationCancelled):c.send_message('t','hi',cancel_event=token)
  c.request.assert_not_called()
 def test_cancel_bypasses_full_queue_and_queued_chat_never_runs(self):
  started=threading.Event();c=Mock();c.start_thread.return_value='t'
  def generate(thread,text,effort=None,cancel_event=None):
   started.set()
   if not cancel_event.wait(2): raise AssertionError('Cancellation blocked behind FIFO')
   raise GenerationCancelled('cancelled')
  c.send_message.side_effect=generate
  def request(i,method,params):return json.dumps({'id':i,'method':method,'params':params})+'\n'
  def source():
   yield request(1,'chat',{'text':'active'})
   if not started.wait(2):raise AssertionError('Worker did not start')
   yield request(2,'chat',{'text':'queued'})
   yield request(3,'chat',{'text':'overflow'})
   yield request('cancel-2','cancel',{'request_id':2})
   yield request('cancel-1','cancel',{'request_id':1})
  sink=io.StringIO();session=StdioSession(c);serve_lines(session,source(),sink,queue_size=1)
  messages=[json.loads(x) for x in sink.getvalue().splitlines()];by_id={m['id']:m for m in messages if 'id'in m}
  self.assertTrue(by_id[1]['result']['cancelled']);self.assertTrue(by_id[2]['result']['cancelled'])
  self.assertEqual(by_id[3]['error']['code'],-32001)
  self.assertTrue(by_id['cancel-1']['result']['requested']);c.send_message.assert_called_once()
  c.send_message.side_effect=None;c.send_message.return_value='next'
  self.assertEqual(session.dispatch('chat',{'text':'next'})['text'],'next')
  c.start_thread.assert_called_once()
 def test_idle_cancel_and_bad_target(self):
  sink=io.StringIO();c=Mock()
  source=io.StringIO('{"id":1,"method":"cancel","params":{"request_id":"gone"}}\n{"id":2,"method":"cancel","params":{"request_id":true}}\n')
  serve_lines(StdioSession(c),source,sink)
  messages=[json.loads(x) for x in sink.getvalue().splitlines()]
  self.assertFalse(messages[1]['result']['requested']);self.assertEqual(messages[2]['error']['code'],-32602)

if __name__=='__main__':unittest.main()
