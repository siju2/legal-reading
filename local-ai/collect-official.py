"""Download public official statutes/judgments; retain raw pages and provenance."""
import hashlib, html, json, re, time
from pathlib import Path
from urllib.parse import urlencode, quote
from urllib.request import Request, urlopen
from datetime import date

ROOT = Path(__file__).parent / 'official-20000'
ROOT.mkdir(exist_ok=True)
TODAY = date.today().isoformat()
CUTOFF = TODAY.replace('-', '')
LAWS = '''민법|형법|상법|민사소송법|형사소송법|민사집행법|행정소송법|행정절차법|행정기본법|국가배상법|근로기준법|노동조합 및 노동관계조정법|근로자퇴직급여 보장법|고용보험법|산업재해보상보험법|산업안전보건법|최저임금법|남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률|기간제 및 단시간근로자 보호 등에 관한 법률|주택임대차보호법|상가건물 임대차보호법|주택법|건축법|건축물관리법|부동산등기법|부동산 거래신고 등에 관한 법률|공인중개사법|집합건물의 소유 및 관리에 관한 법률|국토의 계획 및 이용에 관한 법률|도시 및 주거환경정비법|소득세법|법인세법|부가가치세법|상속세 및 증여세법|국세기본법|국세징수법|지방세법|지방세기본법|조세특례제한법|관세법|소비자기본법|전자상거래 등에서의 소비자보호에 관한 법률|방문판매 등에 관한 법률|약관의 규제에 관한 법률|개인정보 보호법|정보통신망 이용촉진 및 정보보호 등에 관한 법률|저작권법|특허법|상표법|부정경쟁방지 및 영업비밀보호에 관한 법률|도로교통법|교통사고처리 특례법|자동차손해배상 보장법|보험업법|자본시장과 금융투자업에 관한 법률|금융소비자 보호에 관한 법률|신용정보의 이용 및 보호에 관한 법률|채무자 회생 및 파산에 관한 법률|이자제한법|대부업 등의 등록 및 금융이용자 보호에 관한 법률|가사소송법|가족관계의 등록 등에 관한 법률|아동복지법|아동ㆍ청소년의 성보호에 관한 법률|노인복지법|장애인복지법|국민기초생활 보장법|국민연금법|국민건강보험법|의료법|약사법|식품위생법|성폭력범죄의 처벌 등에 관한 특례법|가정폭력범죄의 처벌 등에 관한 특례법|스토킹범죄의 처벌 등에 관한 법률|특정경제범죄 가중처벌 등에 관한 법률|형의 집행 및 수용자의 처우에 관한 법률|경찰관 직무집행법|국가공무원법|지방공무원법|공직선거법|지방자치법|국가를 당사자로 하는 계약에 관한 법률|공공기관의 정보공개에 관한 법률|독점규제 및 공정거래에 관한 법률|하도급거래 공정화에 관한 법률|가맹사업거래의 공정화에 관한 법률|대규모유통업에서의 거래 공정화에 관한 법률|환경정책기본법|폐기물관리법|대기환경보전법|물환경보전법|출입국관리법|국적법|병역법|교육기본법|초ㆍ중등교육법|고등교육법|학교폭력예방 및 대책에 관한 법률|전기통신사업법|전자금융거래법|국유재산법|공유재산 및 물품 관리법|농지법|산지관리법|산림자원의 조성 및 관리에 관한 법률|수산업법|해사안전법|선박법'''.split('|')
TOPICS = '손해배상|임대차|보증금|해고|임금|퇴직금|산업재해|사기|횡령|배임|명예훼손|교통사고|이혼|양육비|상속|유류분|소유권|부당이득|채무불이행|대여금|보증|개인정보|저작권|특허|상표|소비자|계약해제|가맹|파산|조세|행정처분|의료|학교폭력|성폭력|스토킹|가정폭력'.split('|')

def fetch(kind, key, url):
    path = ROOT / f'{kind}-{key}.html'
    if path.exists(): return path.read_text(encoding='utf-8')
    for attempt in range(4):
        try:
            time.sleep(.15)
            with urlopen(Request(url, headers={'User-Agent': 'LegalStudyCorpus/1.0 (public legal research)'}), timeout=45) as response:
                text = response.read().decode('utf-8')
            if len(text) < 1000: raise ValueError('Incomplete official page')
            path.write_text(text, encoding='utf-8')
            return text
        except Exception:
            if attempt == 3: raise
            time.sleep(2 ** attempt)

def clean(markup):
    return ' '.join(html.unescape(re.sub('<[^>]*>', '', markup)).split())

def law_list(markup):
    result = []
    for match in re.finditer(r'onclick="lawInfoPop\((.*?)\);[^\"]*">(.*?)</a>', markup, re.S):
        args = re.findall("'([^']*)'", match[1])
        if len(args) >= 4 and args[3] <= CUTOFF:
            result.append({'seq': args[0], 'name': clean(match[2]), 'effective': args[3]})
    return result

def law_articles(law, markup):
    result = []
    for block in re.findall(r'<div class="lawcon"[^>]*>(.*?)</div>', markup, re.S):
        control = re.search(r'name="joNoList"[^>]*value="(\d+):(\d+):(\d+):\d+"', block)
        label = re.search(r'<label[^>]*>(.*?)</label>', block, re.S)
        if not control or not label: continue
        number, sub, key = control.groups()
        article = f'제{number}조' + (f'의{sub}' if sub != '0' else '')
        heading = clean(label[1])
        if not heading.replace(' ', '').startswith(article): continue
        block = re.sub(r'<label[^>]*>.*?</label>|<span class="sfon"[^>]*>.*?</span>', '', block, flags=re.S)
        body = '\n'.join(filter(None, (clean(p) for p in re.findall(r'<p(?:\s[^>]*)?>(.*?)</p>', block, re.S))))
        if len(body) < 15 or body.startswith('삭제'): continue
        result.append({'id': f'law-{law["seq"]}-{key}', 'documentId': f'law-{law["seq"]}',
            'kind': 'law', 'title': law['name']+' '+heading, 'text': body,
            'url': 'https://www.law.go.kr/법령/'+quote(law['name'])+'/'+quote(article),
            'checkedAt': TODAY, 'effective': law['effective'], 'officialVersion': law['seq'],
            'keywords': [law['name'], article], 'rawFile': f'law-{law["seq"]}.html'})
    return result

def case_ids(markup):
    return list(dict.fromkeys(re.findall(r"precView\('(\d+)'", markup)))

def pieces(text, limit=600):
    # Keep exact contiguous excerpts; boundaries do not imply a complete legal rule.
    text = clean(text)
    while text:
        if len(text) <= limit:
            if len(text) >= 80: yield text
            break
        end = max(text.rfind('. ', 80, limit), text.rfind('다. ', 80, limit))
        end = end+1 if end >= 80 else limit
        yield text[:end].strip()
        text = text[end:].strip()

def case_passages(seq, markup, topic):
    title = re.search(r'<div id="conTop".*?<h2>(.*?)</h2>.*?<span>(.*?)</span>', markup, re.S)
    day = re.search(r'id="precYd" value="(\d+)"', markup)
    no = re.search(r'id="precNo" value="([^"]+)"', markup)
    if not title or not day or not no or day[1] > CUTOFF: return []
    result = []
    # Court-authored issues/holdings only: do not absorb parties' allegations as findings.
    for section, body in re.findall(r'<h4 id="(sa|yo)-[^\"]+">.*?</h4>(.*?)(?=<h4|$)', markup, re.S):
        section_name = '판시사항' if section == 'sa' else '판결요지'
        for para in re.findall(r'<p(?:\s[^>]*)?>(.*?)</p>', body, re.S):
            for part in re.split(r'<br\s*/?>', para):
                for excerpt in pieces(part):
                    result.append({'id': f'prec-{seq}-{len(result)+1}', 'documentId': f'prec-{seq}',
                        'kind': 'case', 'title': clean(title[1])+' '+clean(title[2])+' '+section_name+' 발췌',
                        'text': excerpt, 'url': f'https://www.law.go.kr/LSW/precInfoP.do?precSeq={seq}',
                        'checkedAt': TODAY, 'decisionDate': day[1], 'caseNumber': html.unescape(no[1]),
                        'keywords': [topic, clean(title[1]), html.unescape(no[1])],
                        'section': section_name, 'rawFile': f'case-{seq}.html'})
    return result

def save(name, value):
    (ROOT/name).write_text(json.dumps(value, ensure_ascii=False, indent=1), encoding='utf-8')

def main():
    laws, cases, seen_laws, seen_cases, hashes, errors = [], [], set(), set(), set(), []
    def add(rows, target):
        for row in rows:
            digest = hashlib.sha256(row['text'].encode()).hexdigest()
            if digest not in hashes:
                row['textSha256'] = digest; hashes.add(digest); target.append(row)
    for name in LAWS:
        try:
            url = 'https://www.law.go.kr/LSW/lsSc.do?'+urlencode(dict(menuId=1,subMenuId=15,tabMenuId=81,outmax=150,query=name))
            candidates = law_list(fetch('lawlist', hashlib.sha256(name.encode()).hexdigest()[:12], url))
            for law in candidates:
                if law['seq'] in seen_laws or law['name'] not in (name, name+' 시행령', name+' 시행규칙'): continue
                seen_laws.add(law['seq'])
                url = 'https://www.law.go.kr/LSW/lsInfoR.do?'+urlencode(dict(lsiSeq=law['seq'],efYd=law['effective'],efYn='Y',chrClsCd='010202',nwJoYnInfo='Y',ancYnChk=0,netPrivateYn='N'))
                add(law_articles(law, fetch('law',law['seq'],url)), laws)
            print(json.dumps({'topic':name,'law_articles':len(laws),'law_documents':len(seen_laws)},ensure_ascii=False),flush=True)
            save('laws.json',laws)
            if len(laws)>=20500: break
        except Exception as e:
            errors.append({'topic':name,'error':str(e)}); print(str(e),flush=True)
    for topic in TOPICS:
        try:
            url='https://www.law.go.kr/LSW/precSc.do?'+urlencode(dict(menuId=7,subMenuId=47,tabMenuId=213,outmax=30,query=topic,prncYdStart='19500101',prncYdEnd=CUTOFF))
            for seq in case_ids(fetch('caselist',hashlib.sha256(topic.encode()).hexdigest()[:12],url)):
                if seq in seen_cases: continue
                seen_cases.add(seq)
                try: add(case_passages(seq,fetch('case',seq,f'https://www.law.go.kr/LSW/precInfoP.do?precSeq={seq}'),topic), cases)
                except Exception as e: errors.append({'case':seq,'error':str(e)})
            save('cases.json',cases)
            print(json.dumps({'topic':topic,'case_passages':len(cases),'case_documents':len({r['documentId'] for r in cases})},ensure_ascii=False),flush=True)
            if len(cases)>=3500 and len({r['documentId'] for r in cases})>=200: break
        except Exception as e: errors.append({'topic':topic,'error':str(e)})
    rows=laws+cases
    assert len({r['id'] for r in rows})==len(rows)
    assert len(hashes)==len(rows)
    assert all(r['url'].startswith('https://www.law.go.kr/') for r in rows)
    assert all(r.get('effective',r.get('decisionDate'))<=CUTOFF for r in rows)
    save('corpus.json',rows)
    save('manifest.json',{'checkedAt':TODAY,'law_articles':len(laws),'law_documents':len({r['documentId'] for r in laws}),
        'case_passages':len(cases),'case_documents':len({r['documentId'] for r in cases}),'errors':errors,
        'scope':'Public official statute articles and exact excerpts of judicial issues/holdings; not invented scenarios.'})
    assert len(rows)>=20200 and len(cases)>=2000, 'Insufficient genuine sources; do not pad with duplicates'

if __name__=='__main__': main()
