#!/usr/bin/env bash
set -euo pipefail
project=${1:-"$HOME/practika_ychhhh"}
output=${2:-"$HOME/practika-email-$(date +%Y%m%d-%H%M%S)"}
image=ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2
database=postgres:17.6-alpine
for command in docker python3 gzip; do command -v "$command" >/dev/null; done
project=$(cd "$project" && pwd)
test -f "$project/compose.yaml"
test -f "$project/.github/workflows/ci-cd.yaml"
docker info >/dev/null
docker image inspect "$image" "$database" >/dev/null
if [ -e "$output" ]; then
    printf '%s\n' "Каталог уже существует: $output. Укажите новый каталог вторым аргументом."
    exit 1
fi
umask 077
mkdir -p "$output"
output=$(cd "$output" && pwd)
trap 'rm -f "$output/practika-images.tar.gz.partial"' EXIT
python3 - "$project" "$output" "$image" "$database" <<'PY'
from pathlib import Path
import json
import shutil
import subprocess
import sys
import zipfile

source, output = map(Path, sys.argv[1:3])
image, database = sys.argv[3:5]
package = output / 'practika'
(package / 'scripts').mkdir(parents=True)
compose = (source / 'compose.yaml').read_text()
assert compose.startswith('name: helpdesk\n')
compose = compose.replace('name: helpdesk\n', 'name: practika-review\n', 1)
compose = compose.replace('    build: .\n', '')
(package / 'compose.yaml').write_text(compose)
(package / '.env.example').write_text(
    'DB_NAME=helpdesk\nDB_USER=helpdesk\nDB_PASSWORD=\n'
    'APP_BIND=127.0.0.1\nAPP_PORT=18080\nAPP_IMAGE=' + image + '\n'
)
for name in ('init-env.sh', 'smoke.py', 'seed-demo.py', 'backup.sh', 'restore.sh'):
    shutil.copy2(source / 'scripts' / name, package / 'scripts' / name)
workflow = source / '.github/workflows/ci-cd.yaml'
shutil.copy2(workflow, output / 'ci-cd.yaml')
shutil.copy2(workflow, package / 'ci-cd.yaml')
metadata = []
for tag in (image, database):
    data = json.loads(subprocess.check_output(['docker', 'image', 'inspect', tag], text=True))[0]
    metadata.append({
        'tag': tag,
        'imageId': data['Id'],
        'repoDigests': data.get('RepoDigests', []),
        'os': data['Os'],
        'architecture': data['Architecture'],
    })
(package / 'images.json').write_text(json.dumps(metadata, indent=2) + '\n')
instructions = '''Сервис обработки заявок. Образ и pipeline.

practika-images.tar.gz содержит приложение и PostgreSQL.
Нужны Docker Engine 25+, Docker Compose v2 и Linux amd64.
Python 3 нужен для дополнительной проверки. Сборка исходников не требуется.

Сохранить practika-files.zip и practika-images.tar.gz в одну папку.

unzip practika-files.zip
docker load -i practika-images.tar.gz
cd practika
bash scripts/init-env.sh
docker compose up -d --no-build --pull never --wait --wait-timeout 180

Swagger: http://127.0.0.1:18080/swagger-ui/index.html

Проверка:
curl --fail http://127.0.0.1:18080/actuator/health/readiness
BASE_URL=http://127.0.0.1:18080 python3 scripts/smoke.py

Ожидается UP и PASS. Пароль новой базы генерируется локально.
Compose использует отдельное имя practika-review и новый том для базы.
Остановка с сохранением данных: docker compose down.

Pipeline: ci-cd.yaml. Его путь в исходном проекте:
.github/workflows/ci-cd.yaml. Этапы: тесты, публикация образа,
доставка на runner и проверка API.

Для работы pipeline в другом репозитории нужны исходники проекта,
runner с меткой helpdesk-kali, environment training,
variable STAND_DIR и secret DB_PASSWORD. Для запуска готового образа runner не нужен.

Полный успешный pipeline образа:
https://github.com/Zuika1166/practika_ychhhh/actions/runs/43745510914

Вторая версия и её доставка:
https://github.com/Zuika1166/practika_ychhhh/actions/runs/43756431655

Исходники:
https://github.com/Zuika1166/practika_ychhhh

Идентификаторы образов записаны в images.json.
Для проверки скачанных файлов: sha256sum -c SHA256SUMS.
'''
(package / 'README.txt').write_text(instructions)
with zipfile.ZipFile(output / 'practika-files.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(package.rglob('*')):
        if path.is_file():
            archive.write(path, 'practika/' + str(path.relative_to(package)))
with zipfile.ZipFile(output / 'practika-files.zip') as archive:
    assert archive.testzip() is None
    assert 'practika/.env' not in archive.namelist()
PY
docker image save "$image" "$database" | gzip -1 > "$output/practika-images.tar.gz.partial"
mv "$output/practika-images.tar.gz.partial" "$output/practika-images.tar.gz"
python3 - "$output" <<'PY'
from pathlib import Path
import hashlib
import sys
import tarfile
import json

output = Path(sys.argv[1])
with tarfile.open(output / 'practika-images.tar.gz', 'r:gz') as archive:
    manifest = json.load(archive.extractfile('manifest.json'))
    tags = {tag for item in manifest for tag in item.get('RepoTags', [])}
    assert 'ghcr.io/zuika1166/practika_ychhhh:9eb5ae4585c0965e3387c79b7f47836b89136ac2' in tags
    assert 'postgres:17.6-alpine' in tags
lines = []
for name in ('practika-images.tar.gz', 'practika-files.zip', 'ci-cd.yaml'):
    digest = hashlib.file_digest((output / name).open('rb'), 'sha256').hexdigest()
    lines.append(digest + '  ' + name)
(output / 'SHA256SUMS').write_text('\n'.join(lines) + '\n')
PY
printf '%s\n' "Готово: $output" 'Файлы для передачи: practika-images.tar.gz, practika-files.zip, ci-cd.yaml, SHA256SUMS.'
du -h "$output/practika-images.tar.gz" "$output/practika-files.zip" "$output/ci-cd.yaml"
