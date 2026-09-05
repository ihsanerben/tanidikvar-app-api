"""Offline launcher checks: only temporary fixtures, never the real .env or Docker."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest
import os

SOURCE = pathlib.Path(__file__).resolve().parents[1] / 'run.sh'

class LauncherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        shutil.copyfile(SOURCE, self.root / 'run.sh')
        (self.root / 'scripts').mkdir()
        self.script('scripts/setup-local.sh', 'echo unexpected-setup; exit 99')
        self.script('docker', 'printf "%s\\n" "$*" >> "$CALLS"; exit "${DOCKER_EXIT:-0}"')
        self.env = {**os.environ, 'PATH': str(self.root) + ':' + os.environ['PATH'], 'CALLS': str(self.root / 'calls')}

    def script(self, name, body):
        path = self.root / name
        path.write_text('#!/usr/bin/env bash\n' + body + '\n')
        path.chmod(0o700)

    def run_launcher(self, *args):
        return subprocess.run(['bash', str(self.root / 'run.sh'), *args], cwd='/', env=self.env, text=True, capture_output=True)

    def test_help_and_invalid_arguments_have_no_side_effects(self):
        for args, code in [(('--help',), 0), (('-h',), 0), (('--invalid',), 1), (('--docker', 'extra'), 1)]:
            with self.subTest(args=args):
                result = self.run_launcher(*args)
                self.assertEqual(result.returncode, code)
                self.assertNotIn('unexpected-setup', result.stdout)
                self.assertFalse((self.root / '.env').exists())
                self.assertFalse((self.root / 'calls').exists())

    def test_missing_settings_do_not_trigger_setup(self):
        for mode in ['--status', '--stop']:
            self.assertEqual(self.run_launcher(mode).returncode, 1)
        self.assertFalse((self.root / '.env').exists())
        self.assertFalse((self.root / 'calls').exists())

    def test_status_is_read_only_and_includes_stopped_services(self):
        settings = self.root / '.env'
        settings.write_text('this file must not be sourced by the launcher\n')
        before = settings.read_bytes()
        result = self.run_launcher('--status')
        self.assertEqual(result.returncode, 0)
        self.assertEqual((self.root / 'calls').read_text().strip(), 'compose --profile app ps --all')
        self.assertEqual(settings.read_bytes(), before)

    def test_stop_preserves_volumes_and_reports_failure(self):
        (self.root / '.env').touch()
        self.assertEqual(self.run_launcher('--stop').returncode, 0)
        self.assertEqual((self.root / 'calls').read_text().strip(), 'compose --profile app stop')
        self.env['DOCKER_EXIT'] = '7'
        result = self.run_launcher('--stop')
        self.assertEqual(result.returncode, 7)
        self.assertNotIn('kalıcı veriler korundu', result.stdout)

if __name__ == '__main__':
    unittest.main()
