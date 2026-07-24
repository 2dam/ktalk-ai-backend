import { useState } from 'react'
import axios from 'axios'
import { API_BASE, authHeaders } from '../api'
import ClickableKorean from './ClickableKorean'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

/**
 * 문제 하나: 보기를 누르면 바로 채점하고, 보기별 오답 분석 + 함정 포인트를 펼쳐 보여준다.
 */
function CurriculumProblemCard({ problem }) {
  const [selected, setSelected] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const handleSelect = async (index) => {
    if (selected !== null) return
    setSelected(index)
    setError('')
    try {
      const res = await axios.post(
        `${CURRICULUM_URL}/problems/${problem.id}/answer`,
        { selectedIndex: index },
        { headers: authHeaders({ 'Content-Type': 'application/json; charset=utf-8' }) },
      )
      if (res.data?.success) {
        setResult(res.data.data)
      } else {
        setError(res.data?.message || '채점에 실패했어요.')
      }
    } catch (err) {
      setError(err.response?.data?.message || '채점에 실패했어요.')
    }
  }

  return (
    <div style={{ marginTop: '14px' }}>
      <div style={{ fontWeight: 600, marginBottom: '10px', fontSize: '14px' }}>
        <ClickableKorean text={problem.questionText} />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {problem.options.map((option, idx) => {
          const isSelected = selected === idx
          const isCorrectOption = result && idx === result.correctAnswerIndex
          let backgroundColor = '#fff'
          let borderColor = '#ddd'
          if (result) {
            if (isCorrectOption) {
              backgroundColor = '#f0fdf4'
              borderColor = '#22c55e'
            } else if (isSelected) {
              backgroundColor = '#fef2f2'
              borderColor = '#ef4444'
            }
          }
          return (
            <button
              key={idx}
              type="button"
              onClick={() => handleSelect(idx)}
              disabled={selected !== null}
              style={{
                textAlign: 'left', padding: '10px 14px', borderRadius: '8px',
                border: `1px solid ${borderColor}`, backgroundColor,
                cursor: selected !== null ? 'default' : 'pointer', fontSize: '13px',
              }}
            >
              <div>{idx + 1}. <ClickableKorean text={option} /></div>
              {result && result.optionExplanations?.[idx] && (
                <div style={{ fontSize: '12px', color: '#777', marginTop: '4px' }}>
                  {result.optionExplanations[idx]}
                </div>
              )}
            </button>
          )
        })}
      </div>

      {error && <p style={{ color: '#dc3545', fontSize: '12px', marginTop: '6px' }}>⚠ {error}</p>}

      {result && (
        <div style={{
          marginTop: '10px', padding: '10px 12px', borderRadius: '8px', fontSize: '13px',
          backgroundColor: result.correct ? '#f0fdf4' : '#fffbeb',
          border: '1px solid ' + (result.correct ? '#bbf7d0' : '#fde68a'),
        }}>
          <div style={{ fontWeight: 700, marginBottom: (result.trapNote || result.strategyTip) ? '4px' : 0 }}>
            {result.correct ? '✅ 정답이에요!' : '❌ 아쉬워요, 다시 확인해보세요.'}
          </div>
          {result.trapNote && <div style={{ marginBottom: result.strategyTip ? '4px' : 0 }}>🧠 함정: {result.trapNote}</div>}
          {result.strategyTip && <div>💡 전략 팁: {result.strategyTip}</div>}
        </div>
      )}
    </div>
  )
}

export default CurriculumProblemCard
