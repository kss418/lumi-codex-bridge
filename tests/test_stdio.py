import io
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'bridge'))
from stdio_bridge import StdioSession, serve_lines
from codex_client import CodexError

class StdioTests(unittest.TestCase):
    def run_lines(self, lines, client=None):
        client = client or Mock()
        client.start_thread.return_value = 'thread-1'
        if not isinstance(client.send_message.side_effect, Exception):
            client.send_message.return_value = '안녕하세요\n루미입니다'
        sink = io.StringIO()
        serve_lines(StdioSession(client), io.StringIO('\n'.join(lines)+'\n'), sink)
        return client, [json.loads(line) for line in sink.getvalue().splitlines()]

    def test_unicode_ids_and_reused_thread(self):
        c, out = self.run_lines([json.dumps({'id': i, 'method': 'chat', 'params': {'text': '안녕', 'model': 'm', 'effort': 'low'}}) for i in [1,'second']])
        self.assertEqual(out[0]['event'], 'ready')
        self.assertEqual([x['id'] for x in out[1:]], [1,'second'])
        self.assertEqual(out[1]['result']['text'], '안녕하세요\n루미입니다')
        c.start_thread.assert_called_once_with(model='m')
        self.assertEqual(c.send_message.call_count, 2)

    def test_invalid_input_recovers(self):
        _, out = self.run_lines(['not json', '[]', '{"id":true,"method":"chat"}', '{"id":2,"method":"chat","params":{"text":4}}', '{"id":3,"method":"nope"}', '{"id":4,"method":"shutdown"}'])
        self.assertEqual([x['error']['code'] for x in out[1:-1]], [-32700,-32600,-32600,-32602,-32601])
        self.assertEqual(out[-1]['result'], {'stopping': True})

    def test_shutdown_stops_reading(self):
        c, out = self.run_lines(['{"id":1,"method":"shutdown"}', '{"id":2,"method":"chat","params":{"text":"ignored"}}'])
        self.assertEqual(len(out), 2)
        c.send_message.assert_not_called()

    def test_backend_error_keeps_id(self):
        c = Mock()
        c.send_message.side_effect = CodexError('usage limit')
        _, out = self.run_lines(['{"id":"x","method":"chat","params":{"text":"hello"}}'], c)
        self.assertEqual(out[-1], {'id':'x','error':{'code':-32000,'message':'usage limit'}})

    def test_model_change_rejected_without_losing_thread(self):
        c, out = self.run_lines([json.dumps({'id':i,'method':'chat','params':{'text':'hi','model':m}}) for i,m in [(1,'m'),(2,'other')]])
        self.assertEqual(out[-1]['error']['code'], -32602)
        self.assertEqual(c.send_message.call_count, 1)

if __name__ == '__main__':
    unittest.main()
