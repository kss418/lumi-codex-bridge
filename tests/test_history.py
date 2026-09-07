import sys
import unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'bridge'))
from stdio_bridge import StdioSession, ProtocolError

class HistoryTests(unittest.TestCase):
    def test_restore_once_and_reset(self):
        client=Mock()
        client.start_thread.side_effect=['one','two','three']
        client.send_message.return_value='답변'
        session=StdioSession(client)
        params={'text':'현재 질문','conversation':'a','history':[{'user':'이전 질문','assistant':'이전 답변'}]}
        session.dispatch('chat',params)
        self.assertIn('이전 답변',client.send_message.call_args.args[1])
        self.assertNotIn('이전 답변',str(client.start_thread.call_args))
        session.dispatch('chat',params)
        self.assertEqual(client.send_message.call_args.args[1],'현재 질문')
        session.dispatch('chat',dict(params,conversation='b',history=[]))
        self.assertEqual(client.start_thread.call_count,2)
        self.assertEqual(client.send_message.call_args.args[1],'현재 질문')
        session.dispatch('chat',dict(params,conversation='b',persona='새 페르소나'))
        self.assertEqual(client.start_thread.call_count,3)
        self.assertIn('이전 답변',client.send_message.call_args.args[1])

    def test_reject_malformed_and_oversized_history(self):
        for history in ['invalid',[{'user':'x','assistant':'y','image':'data'}],[{'user':None,'assistant':'y'}],[{'user':'x'*40001,'assistant':'y'}]]:
            client=Mock()
            with self.assertRaises(ProtocolError):
                StdioSession(client).dispatch('chat',{'text':'hi','history':history})
            client.start_thread.assert_not_called()

    def test_history_count_maximum_50(self):
        client=Mock()
        client.send_message.return_value='ok'
        history=[{'user':str(i),'assistant':'reply'} for i in range(50)]
        StdioSession(client).dispatch('chat',{'text':'hi','history':history})
        self.assertIn('49',client.send_message.call_args.args[1])
        with self.assertRaises(ProtocolError):
            StdioSession(client).dispatch('chat',{'text':'hi','history':history+[history[0]]})
