import sys
import unittest
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'bridge'))
from codex_client import CodexClient

MODEL = {'id': 'model-id', 'model': 'example-model', 'supportedReasoningEfforts': [{'reasoningEffort': 'low'}, {'reasoningEffort': 'high'}]}

class SelectionTests(unittest.TestCase):
    def client(self):
        c = CodexClient()
        c._models = [MODEL]
        c.request = Mock(side_effect=[{'thread': {'id': 't'}, 'model': 'example-model'}, {'turn': {'id': 'u'}}])
        c._events.append({'method': 'turn/completed', 'params': {'threadId': 't', 'turn': {'id': 'u', 'status': 'completed', 'items': [{'id': 'i', 'type': 'agentMessage', 'text': 'OK'}]}}})
        return c

    def test_selected_model_and_effort(self):
        c = self.client()
        t = c.start_thread(model='model-id')
        self.assertEqual(c.send_message(t, 'hello', effort='high'), 'OK')
        self.assertEqual(c.request.call_args_list[0].args[1]['model'], 'example-model')
        self.assertEqual(c.request.call_args_list[1].args[1]['effort'], 'high')

    def test_defaults_are_not_overridden(self):
        c = self.client()
        t = c.start_thread()
        c.send_message(t, 'hello')
        self.assertNotIn('model', c.request.call_args_list[0].args[1])
        self.assertNotIn('effort', c.request.call_args_list[1].args[1])

    def test_unsupported_effort_does_not_start_turn(self):
        c = self.client()
        t = c.start_thread()
        with self.assertRaisesRegex(ValueError, 'Supported: low, high'):
            c.send_message(t, 'hello', effort='ultra')
        self.assertEqual(c.request.call_count, 1)

    def test_unknown_model_does_not_start_thread(self):
        c = self.client()
        with self.assertRaisesRegex(ValueError, 'Unknown model'):
            c.start_thread(model='missing')
        c.request.assert_not_called()

    def test_model_list_pagination(self):
        c = CodexClient()
        c.request = Mock(side_effect=[{'data': [MODEL], 'nextCursor': 'next'}, {'data': [], 'nextCursor': None}])
        self.assertEqual(c.list_models(), [MODEL])
        self.assertEqual(c.request.call_args.args, ('model/list', {'cursor': 'next'}))

if __name__ == '__main__':
    unittest.main()
