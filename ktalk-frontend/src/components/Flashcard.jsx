import { useEffect, useState } from 'react'
import axios from 'axios'
import { FLASHCARD_URL, authHeaders } from '../api'

// ecue의 뒤집기 복습(플래시카드)을 한국어 학습용으로 차용.
// 카드는 손으로 채우지 않아도 찬다: 클립 하이라이트, 지문 단어 추출, 튜터 배치 생성.
// 복습은 SM-2 간격 반복(boxLevel 1~5)으로 자동 스케줄링된다.
export default function Flashcard({ context }) {
  const [cards, setCards] = useState([])
  const [due, setDue] = useState([])
  const [flipped, setFlipped] = useState({})
  const [front, setFront] = useState('')
  const [back, setBack] = useState('')
  const [error, setError] = useState('')
  const [tab, setTab] = useState('due') // 'due' | 'all'

  const refresh = async () => {
    try {
      const [allRes, dueRes] = await Promise.all([
        axios.get(FLASHCARD_URL, { headers: authHeaders() }),
        axios.get(`${FLASHCARD_URL}/due`, { headers: authHeaders() }),
      ])
      setCards(allRes.data?.data || [])
      setDue(dueRes.data?.data || [])
    } catch (err) {
      setError(err.response?.data?.message || '카드를 불러오지 못했어요.')
    }
  }

  useEffect(() => { refresh() }, [])

  const addCard = async (e) => {
    e.preventDefault()
    if (!front.trim() || !back.trim()) return
    try {
      await axios.post(FLASHCARD_URL, { front, back, source: 'manual' }, { headers: authHeaders() })
      setFront(''); setBack('')
      refresh()
    } catch (err) {
      setError(err.response?.data?.message || '카드 추가 실패')
    }
  }

  // 컨텍스트(현재 지문)에서 핵심 단어를 카드로 배치 생성 (튜터 경유는 추후)
  const autoFromContext = async () => {
    if (!context || !context.trim()) {
      setError('현재 학습 중인 문장이 없어요. 관심사부터 시작해주세요.')
      return
    }
    const words = context.split(/\s+/).filter((w) => w.length > 1).slice(0, 5)
    try {
      for (const w of words) {
        await axios.post(FLASHCARD_URL, { front: w, back: '(뜻을 직접 적어주세요)', source: 'context' }, { headers: authHeaders() })
      }
      refresh()
    } catch (err) {
      setError(err.response?.data?.message || '자동 생성 실패')
    }
  }

  const review = async (id, quality) => {
    try {
      await axios.post(`${FLASHCARD_URL}/${id}/review`, { quality }, { headers: authHeaders() })
      refresh()
    } catch (err) {
      setError(err.response?.data?.message || '복습 반영 실패')
    }
  }

  const list = tab === 'due' ? due : cards

  return (
    <div>
      <h3 style={{ marginTop: 0 }}>🃏 플래시카드 복습</h3>
      <p style={{ color: '#666', fontSize: '13px' }}>
        매일 조금씩, 누적이 실력입니다. 맞출수록 복습 간격이 길어져요(SM-2).
      </p>

      {error && <p style={{ color: '#dc3545', fontSize: '13px' }}>⚠ {error}</p>}

      <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
        <button type="button" onClick={() => setTab('due')}
          style={tabBtn(tab === 'due')}>복습할 카드 ({due.length})</button>
        <button type="button" onClick={() => setTab('all')}
          style={tabBtn(tab === 'all')}>전체 ({cards.length})</button>
      </div>

      <form onSubmit={addCard} style={{ display: 'flex', gap: '6px', marginBottom: '10px', flexWrap: 'wrap' }}>
        <input value={front} onChange={(e) => setFront(e.target.value)} placeholder="앞면(한국어)"
          style={{ flex: 1, minWidth: '120px', padding: '8px' }} />
        <input value={back} onChange={(e) => setBack(e.target.value)} placeholder="뒷면(뜻)"
          style={{ flex: 1, minWidth: '120px', padding: '8px' }} />
        <button type="submit" style={addBtn}>추가</button>
      </form>
      <button type="button" onClick={autoFromContext}
        style={{ ...addBtn, background: '#fff', color: '#555', border: '1px solid #ccc', marginBottom: '14px' }}>
        현재 문장에서 카드 자동 만들기
      </button>

      {list.length === 0 && (
        <p style={{ color: '#999', fontSize: '13px' }}>아직 카드가 없어요. 위에서 추가하거나 클립에서 만들어보세요.</p>
      )}

      <div style={{ display: 'grid', gap: '10px' }}>
        {list.map((c) => (
          <div key={c.id} style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '12px' }}>
            <div style={{ fontWeight: 700, fontSize: '16px' }}>{c.front}</div>
            {flipped[c.id] ? (
              <div style={{ color: '#555', marginTop: '4px' }}>{c.back}</div>
            ) : (
              <button type="button" onClick={() => setFlipped((f) => ({ ...f, [c.id]: true }))}
                style={{ background: 'none', border: 'none', color: '#999', cursor: 'pointer', fontSize: '13px', padding: 0 }}>
                뜻 보기
              </button>
            )}
            <div style={{ marginTop: '8px', display: 'flex', gap: '6px' }}>
              <button type="button" onClick={() => review(c.id, 5)}
                style={revBtn('#16a34a')}>알아요</button>
              <button type="button" onClick={() => review(c.id, 1)}
                style={revBtn('#dc3545')}>몰라요</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function tabBtn(active) {
  return {
    padding: '6px 12px', borderRadius: '999px', cursor: 'pointer', fontSize: '13px',
    border: '1px solid #ccc', backgroundColor: active ? '#0ea5e9' : '#fff', color: active ? '#fff' : '#555',
  }
}
const addBtn = {
  padding: '8px 14px', borderRadius: '6px', border: 'none', background: '#0ea5e9', color: '#fff', cursor: 'pointer', fontSize: '14px',
}
function revBtn(color) {
  return { padding: '6px 12px', borderRadius: '6px', border: 'none', background: color, color: '#fff', cursor: 'pointer', fontSize: '13px' }
}
