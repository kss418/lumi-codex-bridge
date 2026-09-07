import io
import json
import sys
import threading
import unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'bridge'))
from stdio_bridge import StdioSession, serve_lines
from codex_client import CodexError


def request(i, method='chat'):
    return json.dumps({'id': i, 'method': method, 'params': {'text': str(i)}})+'\n'


class QueueTests(unittest.TestCase):
    def test_accepts_input_during_generation_and_drains_before_shutdown(self):
        queued = threading.Event()
        calls = []
        c = Mock()
        c.start_thread.return_value = 't'

        def generate(thread_id, text, effort=None, cancel_event=None):
            if text == 'direct':
                if not queued.wait(2):
                    raise AssertionError('Input reader stalled during generation')
            calls.append(text)
            return 'reply:' + text

        c.send_message.side_effect = generate

        def source():
            yield request('direct')
            yield request('self-talk')
            queued.set()
            yield request('stop', 'shutdown')
            raise AssertionError('Reader consumed input after shutdown')

        sink = io.StringIO()
        serve_lines(StdioSession(c), source(), sink, queue_size=3)
        output = [json.loads(line) for line in sink.getvalue().splitlines()]
        self.assertEqual(calls, ['direct', 'self-talk'])
        self.assertEqual([item['id'] for item in output[1:]], ['direct', 'self-talk', 'stop'])
        self.assertEqual(output[1]['result']['text'], 'reply:direct')
        self.assertEqual(output[2]['result']['text'], 'reply:self-talk')
        self.assertTrue(output[3]['result']['stopping'])
        c.start_thread.assert_called_once()

    def test_bounded_queue_preserves_all_requests_and_continues_after_error(self):
        c = Mock()
        c.start_thread.return_value = 't'
        def generate(thread_id, text, effort=None, cancel_event=None):
            if text == '20':
                raise CodexError('test failure')
            return text
        c.send_message.side_effect = generate
        sink = io.StringIO()
        serve_lines(StdioSession(c), (request(i) for i in range(100)), sink, queue_size=128)
        output = [json.loads(line) for line in sink.getvalue().splitlines()][1:]
        self.assertEqual([item['id'] for item in output], list(range(100)))
        self.assertEqual(output[20]['error']['code'], -32000)
        self.assertEqual(output[-1]['result']['text'], '99')
        self.assertEqual(c.send_message.call_count, 100)


if __name__ == '__main__':
    unittest.main()
