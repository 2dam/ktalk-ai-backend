import { useEffect, useState } from 'react'
import axios from 'axios'
import { API_BASE, authHeaders, hasToken } from '../api'
import { TAB_COLORS } from '../theme'
import ClickableKorean from './ClickableKorean'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

const ACCENT = TAB_COLORS.navigation.accent

/** 오답노트 카드 하나 — 이미 결과를 알고 있으니 채점 없이 정답/오답을 바로 색으로 보여준다. */
function WrongNoteCard({ note, onRemove }) {
  const [removing, setRemoving] = useState(false)

  const handleRemove = async () => {
    setRemoving(true)
    try {
      await axios.delete(`${CURRICULUM_URL}/wrong-notes/${note.problemId}`, { headers: authHeaders() })
      onRemove(note.problemId)
    } catch {
      setRemoving(false)
    }
  }

  return (
    <div style={{ padding: '16px', border: '1px solid #eee', borderRadius: '10px', marginBottom: '14px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px', flexWrap: 'wrap' }}>
        <span style={{
          fontSize: '12px', padding: '2px 8px', borderRadius: '999px',
          backgroundColor: '#eef2ff', color: '#4338ca',
        }}>
          {note.passageCategory === 'LISTENING' ? '듣기' : '읽기'}
          {note.passageSubType ? ` · ${note.passageSubType}` : ''}
        </span>
        <button
          type="button"
          onClick={handleRemove}
          disabled={removing}
          style={{
            marginLeft: 'auto', border: 'none', background: 'none', cursor: removing ? 'not-allowed' : 'pointer',
            fontSize: '12px', color: '#999',
          }}
        >
          {removing ? '지우는 중...' : '✕ 복습 완료, 지우기'}
        </button>
      </div>

      {note.passageText && (
        <div style={{ whiteSpace: 'pre-wrap', fontSize: '13px', lineHeight: 1.6, color: '#555', marginBottom: '10px' }}>
          <ClickableKorean text={note.passageText} />
        </div>
      )}

      <div style={{ fontWeight: 600, marginBottom: '10px', fontSize: '14px' }}>
        <ClickableKorean text={note.questionText} />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {note.options.map((option, idx) => {
          const isCorrectOption = idx === note.correctAnswerIndex
          const isMySelection = idx === note.selectedIndex
          let backgroundColor = '#fff'
          let borderColor = '#ddd'
          if (isCorrectOption) {
            backgroundColor = '#f0fdf4'
            borderColor = '#22c55e'
          } else if (isMySelection) {
            backgroundColor = '#fef2f2'
            borderColor = '#ef4444'
          }
          return (
            <div key={idx} style={{ textAlign: 'left', padding: '10px 14px', borderRadius: '8px', border: `1px solid ${borderColor}`, backgroundColor, fontSize: '13px' }}>
              <div>
                {idx + 1}. <ClickableKorean text={option} />
                {isMySelection && !isCorrectOption && <span style={{ marginLeft: '6px', color: '#ef4444' }}>← 내가 고른 답</span>}
                {isCorrectOption && <span style={{ marginLeft: '6px', color: '#22c55e' }}>← 정답</span>}
              </div>
              {note.optionExplanations?.[idx] && (
                <div style={{ fontSize: '12px', color: '#777', marginTop: '4px' }}>{note.optionExplanations[idx]}</div>
              )}
            </div>
          )
        })}
      </div>

      {(note.trapNote || note.strategyTip) && (
        <div style={{ marginTop: '10px', padding: '10px 12px', borderRadius: '8px', fontSize: '13px', backgroundColor: '#fffbeb', border: '1px solid #fde68a' }}>
          {note.trapNote && <div style={{ marginBottom: note.strategyTip ? '4px' : 0 }}>🧠 함정: {note.trapNote}</div>}
          {note.strategyTip && <div>💡 전략 팁: {note.strategyTip}</div>}
        </div>
      )}
    </div>
  )
}

/**
 * 오답노트 목록(로딩/빈 상태/카드 포함). 로그인된 상태에서만 쓴다. 오답노트 화면과, 지문이 없는
 * 복습일(채점 후 오답 정리 등)의 하루 학습 화면이 함께 쓴다.
 */
export function WrongNotesPanel() {
  const [notes, setNotes] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      setError('')
      try {
        const res = await axios.get(`${CURRICULUM_URL}/wrong-notes`, { headers: authHeaders() })
        if (res.data?.success) {
          setNotes(res.data.data)
        } else {
          setError(res.data?.message || '오답노트를 불러오지 못했어요.')
        }
      } catch (err) {
        setError(err.response?.data?.message || '오답노트를 불러오지 못했어요.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const handleRemove = (problemId) => {
    setNotes((prev) => (prev ? prev.filter((n) => n.problemId !== problemId) : prev))
  }

  return (
    <>
      {loading && <p>불러오는 중...</p>}
      {!loading && error && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}
      {!loading && !error && notes?.length === 0 && (
        <p style={{ color: '#666' }}>아직 틀린 문제가 없어요. 문제를 풀다 틀리면 여기 자동으로 쌓여요!</p>
      )}
      {!loading && !error && notes?.length > 0 && (
        <>
          <p style={{ fontSize: '13px', color: ACCENT, marginTop: 0, marginBottom: '16px' }}>
            총 {notes.length}개의 복습할 문제가 있어요.
          </p>
          {notes.map((note) => (
            <WrongNoteCard key={note.problemId} note={note} onRemove={handleRemove} />
          ))}
        </>
      )}
    </>
  )
}

function WrongNoteList({ onBack, onRequireAuth }) {
  const loggedIn = hasToken()

  if (!loggedIn) {
    return (
      <main className="topik-page" id="top">
        <div className="topik-page-head">
          <button type="button" className="topik-back" onClick={onBack}>← TOPIK 메뉴로</button>
          <span className="topik-badge">TOPIK 코스</span>
          <h1>로그인하고 오답노트를 확인하세요</h1>
          <p>틀린 문제만 모아 반복 학습하고 취약점을 보완해요.</p>
        </div>
        <button
          type="button"
          className="primary-cta"
          onClick={onRequireAuth}
          style={{ margin: '0 auto', display: 'block' }}
        >
          로그인하기
        </button>
      </main>
    )
  }

  return (
    <main className="topik-page" id="top">
      <div className="topik-page-head">
        <button type="button" className="topik-back" onClick={onBack}>← TOPIK 메뉴로</button>
        <span className="topik-badge">TOPIK 코스</span>
        <h1>오답노트</h1>
        <p>틀린 문제만 모아 반복 학습하고 취약점을 보완해요.</p>
      </div>

      <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
        <WrongNotesPanel />
      </div>
    </main>
  )
}

export default WrongNoteList
