import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request

from yt_dlp.postprocessor.common import PostProcessor
from yt_dlp.utils import PostProcessingError

API = 'https://lrclib.net/api'
USER_AGENT = 'YTDLnis (https://github.com/deniscerri/ytdlnis)'
DURATION_TOLERANCE = 5

_LRC_STAMP = re.compile(r'\[(\d+):(\d+(?:\.\d+)?)\]')
_WORD_STAMP = re.compile(r'<(\d+):(\d+(?:\.\d+)?)>')
_JUNK_BRACKETS = re.compile(
    r'\s*[\(\[][^\)\]]*(?:official|video|audio|lyric|visuali[sz]er|\bhd\b|\b4k\b|\bmv\b|remaster)[^\)\]]*[\)\]]',
    re.IGNORECASE)


def _get_json(path, params=None):
    url = f'{API}/{path}'
    if params:
        url += '?' + urllib.parse.urlencode({k: v for k, v in params.items() if v not in (None, '')})
    req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return json.loads(res.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def parse_lrc(text):
    """Returns a sorted list of (milliseconds, line)."""
    lines = []
    for raw in (text or '').splitlines():
        stamps = list(_LRC_STAMP.finditer(raw))
        if not stamps:
            stamps = list(_WORD_STAMP.finditer(raw))
        if not stamps:
            continue
        content = raw[stamps[-1].end():].strip()
        for s in stamps:
            ms = int((int(s.group(1)) * 60 + float(s.group(2))) * 1000)
            lines.append((ms, content))
    lines.sort(key=lambda l: l[0])
    return lines


def format_lrc(lines, offset=0):
    out = []
    for ms, content in lines:
        ms = max(0, ms + offset)
        out.append(f'[{ms // 60000:02d}:{(ms % 60000) / 1000:05.2f}]{content}')
    return '\n'.join(out)


def _ttml_clock(ms):
    ms = max(0, int(ms))
    hours, rest = divmod(ms, 3_600_000)
    minutes, rest = divmod(rest, 60_000)
    seconds, millis = divmod(rest, 1_000)
    return f'{hours:02d}:{minutes:02d}:{seconds:02d}.{millis:03d}'


def _xml_escape(text):
    return (text.replace('&', '&amp;')
                .replace('<', '&lt;')
                .replace('>', '&gt;'))


def _format_ttml_content(content, line_begin, line_end, offset=0):
    matches = list(_WORD_STAMP.finditer(content))
    if not matches:
        return _xml_escape(_WORD_STAMP.sub('', content).strip())
    spans = []
    for i, m in enumerate(matches):
        w_ms = int((int(m.group(1)) * 60 + float(m.group(2))) * 1000)
        w_begin = max(0, w_ms + offset)
        text_start = m.end()
        text_end = matches[i + 1].start() if i + 1 < len(matches) else len(content)
        w_text = content[text_start:text_end].strip()
        if not w_text:
            continue
        if i + 1 < len(matches):
            next_ms = int((int(matches[i + 1].group(1)) * 60 + float(matches[i + 1].group(2))) * 1000)
            w_end = max(w_begin + 1, next_ms + offset)
        else:
            w_end = max(w_begin + 1, line_end)
        spans.append(f'<span begin="{_ttml_clock(w_begin)}" end="{_ttml_clock(w_end)}">{_xml_escape(w_text)}</span>')
    return ' '.join(spans) if spans else _xml_escape(_WORD_STAMP.sub('', content).strip())


def format_ttml(lines, offset=0, line_duration=5000):
    """Apple Music-style TTML built from line and word timings.

    Handles line timings from lrclib as well as syllable/word timings from ELRC
    (<mm:ss.xx> tags), generating proper Apple Music <span begin="" end=""> word spans.
    """
    body = []
    for index, (ms, content) in enumerate(lines):
        if not content:
            continue
        begin = max(0, ms + offset)
        if index + 1 < len(lines):
            end = max(begin + 1, lines[index + 1][0] + offset)
        else:
            end = begin + line_duration
        body.append(
            f'      <p begin="{_ttml_clock(begin)}" end="{_ttml_clock(end)}">'
            f'{_format_ttml_content(content, begin, end, offset)}</p>')
    if not body:
        return ''
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<tt xmlns="http://www.w3.org/ns/ttml" '
            'xmlns:tts="http://www.w3.org/ns/ttml#styling" xml:lang="und">\n'
            '  <head>\n    <metadata/>\n  </head>\n  <body>\n    <div>\n'
            + '\n'.join(body) +
            '\n    </div>\n  </body>\n</tt>')


def _pick(results, duration):
    best = None
    for r in results or []:
        if r.get('instrumental'):
            continue
        if not (r.get('syncedLyrics') or r.get('plainLyrics')):
            continue
        diff = abs((r.get('duration') or 0) - duration) if duration else 0
        if duration and diff > DURATION_TOLERANCE:
            continue
        # synced lyrics first, then closest duration
        key = (0 if r.get('syncedLyrics') else 1, diff)
        if best is None or key < best[0]:
            best = (key, r)
    return best[1] if best else None


def find_lyrics(track, artist, album, duration, lrclib_id=None):
    if lrclib_id:
        return _get_json(f'get/{lrclib_id}')

    track_clean = _JUNK_BRACKETS.sub('', track).strip()
    if track_clean.lower().startswith(artist.lower() + ' - '):
        track_clean = track_clean[len(artist) + 3:]

    if artist:
        res = _get_json('get', {
            'track_name': track_clean, 'artist_name': artist,
            'album_name': album, 'duration': duration or None})
        if res and (res.get('syncedLyrics') or res.get('plainLyrics')):
            return res

    for params in (
        {'track_name': track_clean, 'artist_name': artist},
        {'q': f'{artist} {track_clean}'.strip()},
    ):
        res = _pick(_get_json('search', params), duration)
        if res:
            return res
    return None


def embed(path, text, synced):
    ext = os.path.splitext(path)[1].lower().lstrip('.')

    if ext == 'mp3':
        from mutagen.id3 import ID3, ID3NoHeaderError, SYLT, USLT
        try:
            tags = ID3(path)
        except ID3NoHeaderError:
            tags = ID3()
        tags.delall('USLT')
        tags.delall('SYLT')
        tags.add(USLT(encoding=3, lang='eng', desc='', text=text))
        if synced:
            tags.add(SYLT(encoding=3, lang='eng', format=2, type=1, desc='',
                          text=[(content, ms) for ms, content in synced]))
        tags.save(path)
    elif ext in ('m4a', 'mp4'):
        from mutagen.mp4 import MP4
        audio = MP4(path)
        if audio.tags is None:
            audio.add_tags()
        audio.tags['\xa9lyr'] = [text]
        audio.save()
    elif ext in ('opus', 'ogg', 'oga', 'flac'):
        import mutagen
        audio = mutagen.File(path)
        if audio is None:
            raise ValueError('unsupported file')
        audio['lyrics'] = [text]
        audio.save()
    else:
        raise ValueError(f'embedding lyrics is not supported for .{ext} files')


class LyricsPP(PostProcessor):
    """Fetches lyrics from lrclib.net and embeds them into the audio file.

    Raises PostProcessingError when the lyrics can't be fetched or aren't found,
    which fails the download.

    Must run with when=after_move: post_process plugins run before audio
    extraction, so the file would still be the original webm/m4a stream.

    Args (all optional):
      id       lrclib track id, skips matching when set
      offset   milliseconds added to every synced timestamp
      embed    1/0 write the lyrics into the file tags
      ttml     1/0 write Apple Music-style TTML instead of LRC when embedding
      lrc      1/0 save a .lrc file next to the audio file when synced lyrics exist
    """

    def __init__(self, downloader=None, id=None, offset=0, embed=1, ttml=1, lrc=0, file=None, **kwargs):
        super().__init__(downloader)
        self._id = int(id) if id and str(id).isdigit() else None
        self._offset = int(offset or 0)
        self._embed = str(embed) != '0'
        self._ttml = str(ttml) == '1'
        self._lrc = str(lrc) == '1'
        self._file = file or kwargs.get('text_file')

    @staticmethod
    def _artist(info):
        artist = info.get('artist') or ''
        if not artist and info.get('artists'):
            artist = info['artists'][0]
        artist = artist or info.get('uploader') or info.get('channel') or ''
        artist = re.sub(r'\s*-\s*Topic$', '', artist)
        return artist.split(',')[0].strip()

    def run(self, info):
        path = info.get('filepath')
        if not path or not os.path.exists(path):
            return [], info

        text = None
        synced = []
        shifted = []
        ttml = ''

        if self._file and os.path.exists(self._file):
            try:
                with open(self._file, 'r', encoding='utf-8') as f:
                    content = f.read().strip()
                if content:
                    if '<tt' in content or '<?xml' in content:
                        text = content
                        ttml = content
                    else:
                        synced = parse_lrc(content)
                        shifted = [(max(0, ms + self._offset), c) for ms, c in synced]
                        plain = '\n'.join(_WORD_STAMP.sub('', c).strip() for _, c in synced) if synced else _WORD_STAMP.sub('', content).strip()
                        text = format_lrc(synced, self._offset) if synced else plain
                        if self._ttml and synced:
                            ttml = format_ttml(synced, self._offset)
                            text = ttml
            except Exception as e:
                self.report_warning(f'Could not read custom lyrics file: {e}')

        if not text:
            track = info.get('track') or info.get('title') or ''
            try:
                result = find_lyrics(track, self._artist(info), info.get('album') or '',
                                     int(info.get('duration') or 0), self._id)
            except Exception as e:
                raise PostProcessingError(f'Could not fetch lyrics: {e}')

            if not result or result.get('instrumental'):
                self.to_screen('No lyrics found')
                return [], info

            synced = parse_lrc(result.get('syncedLyrics'))
            shifted = [(max(0, ms + self._offset), c) for ms, c in synced]
            plain = result.get('plainLyrics') or '\n'.join(c for _, c in synced)
            # Only mp3 has a real synced tag (SYLT). Everywhere else players expect
            # LRC text, with timestamps, inside the regular lyrics tag.
            text = format_lrc(synced, self._offset) if synced else plain
            ttml = format_ttml(synced, self._offset) if (self._ttml and synced) else ''
            if ttml:
                # TTML carries its own timing, so no separate SYLT frame is written next to it.
                text = ttml

        if self._embed:
            try:
                embed(path, text, None if ttml else shifted)
                if ttml:
                    self.to_screen('Embedded lyrics (synced, TTML)')
                else:
                    self.to_screen('Embedded lyrics' + (' (synced, LRC)' if shifted else ' (plain)'))
            except Exception as e:
                self.report_warning(f'Could not embed lyrics: {e}')

        if self._lrc and shifted:
            lrc_path = os.path.splitext(path)[0] + '.lrc'
            try:
                with open(lrc_path, 'w', encoding='utf-8') as f:
                    f.write(format_lrc(synced, self._offset))
                self.to_screen(f'Saved {os.path.basename(lrc_path)}')
            except OSError as e:
                self.report_warning(f'Could not save lyrics file: {e}')

        return [], info
