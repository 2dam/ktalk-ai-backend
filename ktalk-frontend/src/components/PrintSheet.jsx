import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import axios from 'axios'
import { API_BASE, authHeaders } from '../api'
import { Bi } from '../EnglishHint'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`
const CIRCLED = ['①', '②', '③', '④', '⑤']

const MODES = [
  { id: 'questions', ko: '문제지만 인쇄', en: 'Questions only' },
  { id: 'answers', ko: '정답·해설지만 인쇄', en: 'Answers & explanations only' },
  { id: 'both', ko: '문제지 + 정답·해설지', en: 'Questions + answers' },
]

// "모의고사 1회 - 듣기 1~10번 (이어질 …)" 같은 일자 과제 문구에서 구간 이름("듣기 1~10번")만 뽑는다.
function dayLabel(day) {
  const after = day.task.includes(' - ') ? day.task.split(' - ').slice(1).join(' - ') : day.task
  const short = after.split(/[(（]/)[0].trim()
  return short && short.length <= 40 ? short : `${day.dayNumber}일째`
}

// 하루치/한 주차의 문제를 번호 순서대로 한 줄로 펼친다. 인쇄 문제 번호는 이 순서의 1부터 시작한다.
function flatten(data) {
  const items = []
  let no = 0
  data.days.forEach((day) => {
    day.passages.forEach((passage) => {
      passage.problems.forEach((problem) => {
        no += 1
        items.push({ no, day, passage, problem })
      })
    })
  })
  return items
}

function QuestionSheet({ data, multiDay }) {
  // 문제 번호는 렌더 중 변수를 바꾸지 않고 미리 계산해 둔다(문제 객체 -> 번호).
  const items = flatten(data)
  const numbers = new Map(items.map((item) => [item.problem, item.no]))
  return (
    <section className="print-page">
      <header className="print-header">
        <div className="print-kicker">{data.curriculumTitle} · {data.learnerTypeLabel} · {data.levelLabel}</div>
        <h1>{data.title} <small>문제지 / Questions</small></h1>
        <div className="print-fill">
          <span>이름 Name ______________</span>
          <span>날짜 Date ____________</span>
          <span>점수 Score ______ / {items.length}</span>
        </div>
      </header>

      {data.days.map((day) => {
        // 듣기 안내문은 그 구간의 첫 듣기 문제에만 길게, 나머지는 문제 유형만 짧게 보여준다.
        const firstListening = day.passages.findIndex((p) => p.category === 'LISTENING')
        return (
        <div key={day.dayNumber} className="print-day">
          {multiDay && <h2 className="print-day-title">{dayLabel(day)}</h2>}
          {day.passages.map((passage, pIdx) => (
            <div key={pIdx} className="print-passage">
              {passage.category === 'LISTENING' ? (
                <div className="print-listening">
                  🎧 듣기 / Listening{passage.subType ? ` · ${passage.subType}` : ''}
                  {pIdx === firstListening && ' — 앱에서 음성을 들으며 푸세요. (대본은 정답·해설지에 있어요)'}
                </div>
              ) : (
                <>
                  {passage.subType && <div className="print-subtype">읽기 / Reading · {passage.subType}</div>}
                  <div className="print-text">{passage.passageText}</div>
                </>
              )}
              {passage.problems.map((problem, qIdx) => (
                <div key={qIdx} className="print-question">
                  <div className="print-q"><b>{numbers.get(problem)}.</b> {problem.questionText}</div>
                  <ol className="print-options">
                    {problem.options.map((option, oIdx) => (
                      <li key={oIdx}>{CIRCLED[oIdx]} {option}</li>
                    ))}
                  </ol>
                </div>
              ))}
            </div>
          ))}
        </div>
        )
      })}
    </section>
  )
}

function AnswerSheet({ data }) {
  const items = flatten(data)
  const listening = new Map()
  items.forEach((item) => {
    if (item.passage.category === 'LISTENING' && !listening.has(item.passage)) {
      listening.set(item.passage, item.no)
    }
  })

  return (
    <section className="print-page print-answers">
      <header className="print-header">
        <div className="print-kicker">{data.curriculumTitle} · {data.learnerTypeLabel} · {data.levelLabel}</div>
        <h1>{data.title} <small>정답 및 해설 / Answers &amp; Explanations</small></h1>
      </header>

      <div className="print-answer-grid">
        {items.map((item) => (
          <span key={item.no}><b>{item.no}</b> {CIRCLED[item.problem.correctAnswerIndex]}</span>
        ))}
      </div>

      {items.map((item) => {
        const { no, passage, problem } = item
        const firstOfListening = listening.get(passage) === no
        return (
          <div key={no} className="print-explain">
            {firstOfListening && (
              <div className="print-script"><b>🎧 듣기 대본 / Script</b><div>{passage.passageText}</div></div>
            )}
            <div className="print-q"><b>{no}.</b> 정답 {CIRCLED[problem.correctAnswerIndex]} {problem.options[problem.correctAnswerIndex]}</div>
            {problem.optionExplanations && (
              <ul className="print-notes">
                {problem.optionExplanations.map((note, i) => (
                  <li key={i}>{CIRCLED[i]} {note}</li>
                ))}
              </ul>
            )}
            {problem.trapNote && <div className="print-tip">🧠 {problem.trapNote}</div>}
            {problem.strategyTip && <div className="print-tip">💡 {problem.strategyTip}</div>}
          </div>
        )
      })}
    </section>
  )
}

function PrintDocument({ data, mode }) {
  const multiDay = data.days.length > 1
  return (
    <div className="print-root">
      {(mode === 'questions' || mode === 'both') && <QuestionSheet data={data} multiDay={multiDay} />}
      {(mode === 'answers' || mode === 'both') && <AnswerSheet data={data} />}
    </div>
  )
}

/**
 * 인쇄 메뉴 버튼. week(주차 전체) 또는 day(하루치)를 받아 서버에서 정답까지 포함한 데이터를 가져온 뒤
 * 브라우저 인쇄 창을 연다. 인쇄 창에서 "PDF로 저장"을 고르면 파일로 내려받을 수 있다.
 */
function PrintMenu({ week, day, label, labelEn }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [job, setJob] = useState(null) // { data, mode }

  // 인쇄 문서가 DOM에 그려진 다음에 인쇄 창을 연다. 인쇄가 끝나면(또는 취소하면) 문서를 치운다.
  useEffect(() => {
    if (!job) return undefined
    const cleanup = () => {
      document.body.classList.remove('printing')
      setJob(null)
    }
    window.addEventListener('afterprint', cleanup, { once: true })
    document.body.classList.add('printing')
    const timer = setTimeout(() => window.print(), 50)
    return () => {
      clearTimeout(timer)
      window.removeEventListener('afterprint', cleanup)
      document.body.classList.remove('printing')
    }
  }, [job])

  const start = async (mode) => {
    setBusy(true)
    setError('')
    try {
      const params = week != null ? { week } : { day }
      const res = await axios.get(`${CURRICULUM_URL}/print`, { params, headers: authHeaders() })
      if (res.data?.success) {
        setJob({ data: res.data.data, mode })
      } else {
        setError(res.data?.message || '인쇄할 내용을 불러오지 못했어요.')
      }
    } catch (err) {
      setError(err.response?.data?.message || '인쇄할 내용을 불러오지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="print-menu">
      <details>
        <summary className="print-menu-button">
          🖨 <Bi en={labelEn || 'Print / Save as PDF'}>{label || '인쇄 / PDF 저장'}</Bi>
        </summary>
        <div className="print-menu-list">
          {MODES.map((mode) => (
            <button key={mode.id} type="button" disabled={busy} onClick={() => start(mode.id)}>
              <Bi en={mode.en}>{busy ? '준비 중...' : mode.ko}</Bi>
            </button>
          ))}
          <small className="print-menu-hint">
            <Bi en="In the print window, choose &quot;Save as PDF&quot; to download a file.">인쇄 창에서 &quot;PDF로 저장&quot;을 고르면 파일로 받을 수 있어요.</Bi>
          </small>
        </div>
      </details>
      {error && <p className="print-menu-error">⚠ {error}</p>}
      {job && createPortal(<PrintDocument data={job.data} mode={job.mode} />, document.body)}
    </div>
  )
}

export default PrintMenu
