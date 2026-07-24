import { useEffect, useState } from 'react'
import axios from 'axios'
import { TOPIK_URL, authHeaders, hasToken } from '../api'
import { TAB_COLORS } from '../theme'
import ClickableKorean from './ClickableKorean'

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_DARK = TAB_COLORS.navigation.dark
const ACCENT_TINT = TAB_COLORS.navigation.tint

const LEVEL_LABELS = {
  LEVEL_1: '1급', LEVEL_2: '2급', LEVEL_3: '3급', LEVEL_4: '4급', LEVEL_5: '5급', LEVEL_6: '6급',
}
const GROUP_LABELS = { LOWER: '하급', MIDDLE: '중급', UPPER: '상급' }

// 백엔드가 5문제마다 정답률로 등급을 자동 조정하기 때문에, 급수 카드 3개는 모두 이
// 화면으로 들어온다 — 몇 급으로 시작하든 몇 문제 안에 실제 실력에 맞게 수렴한다.
function TopikQuiz({ onBack, onRequireAuth }) {
  const loggedIn = hasToken()

  const [progress, setProgress] = useState(null)
  const [question, setQuestion] = useState(null)
  const [selected, setSelected] = useState(null)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [noItems, setNoItems] = useState(false)
  const [generating, setGenerating] = useState(false)

  const loadProgress = async () => {
    try {
      const res = await axios.get(`${TOPIK_URL}/quiz/progress`, { headers: authHeaders() })
      if (res.data?.success) setProgress(res.data.data)
    } catch {
      // 진행 상황은 참고용이라 실패해도 문제 풀이 자체는 막지 않는다.
    }
  }

  const loadNextQuestion = async () => {
    setLoading(true)
    setError('')
    setNoItems(false)
    setSelected(null)
    setResult(null)
    try {
      const res = await axios.get(`${TOPIK_URL}/quiz/next`, { headers: authHeaders() })
      if (res.data?.success) {
        setQuestion(res.data.data)
      } else {
        setError(res.data?.message || '문제를 불러오지 못했어요.')
      }
    } catch (err) {
      const message = err.response?.data?.message || '문제를 불러오지 못했어요.'
      setError(message)
      if (message.includes('출제할 수 있는 문항이 없습니다')) {
        setNoItems(true)
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!loggedIn) {
      setLoading(false)
      return
    }
    loadProgress()
    loadNextQuestion()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleGenerate = async () => {
    setGenerating(true)
    setError('')
    try {
      await axios.post(`${TOPIK_URL}/quiz-items/generate-all`, null, { headers: authHeaders() })
      await loadNextQuestion()
    } catch (err) {
      setError(err.response?.data?.message || '문항 생성에 실패했어요.')
    } finally {
      setGenerating(false)
    }
  }

  const handleSelect = async (index) => {
    if (selected !== null || !question) return
    setSelected(index)
    try {
      const res = await axios.post(
        `${TOPIK_URL}/quiz/${question.id}/answer`,
        { selectedIndex: index },
        { headers: authHeaders({ 'Content-Type': 'application/json; charset=utf-8' }) },
      )
      if (res.data?.success) {
        const data = res.data.data
        setResult(data)
        setProgress({
          topikLevel: data.currentLevel,
          topikGroup: data.currentGroup,
          attemptCount: data.attemptCount,
          correctCount: data.correctCount,
          accuracy: data.attemptCount ? data.correctCount / data.attemptCount : 0,
        })
      } else {
        setError(res.data?.message || '채점에 실패했어요.')
      }
    } catch (err) {
      setError(err.response?.data?.message || '채점에 실패했어요.')
      setSelected(null)
    }
  }

  if (!loggedIn) {
    return (
      <main className="topik-page" id="top">
        <div className="topik-page-head">
          <button type="button" className="topik-back" onClick={onBack}>← TOPIK 메뉴로</button>
          <span className="topik-badge">TOPIK 코스</span>
          <h1>로그인하고 실력에 맞는 문제를 풀어보세요</h1>
          <p>정답률에 따라 자동으로 난이도가 조정되는 적응형 퀴즈예요.</p>
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
        <h1>적응형 TOPIK 퀴즈</h1>
        <p>정답률이 쌓일수록 난이도가 자동으로 오르내려요. 모르는 단어는 눌러서 뜻을 확인하세요.</p>
      </div>

      {progress && (
        <div style={{
          display: 'flex', alignItems: 'center', gap: '14px', padding: '14px 18px', borderRadius: '12px',
          backgroundColor: ACCENT_TINT, marginBottom: '20px', flexWrap: 'wrap',
        }}>
          <span style={{ fontWeight: 700, fontSize: '16px', color: ACCENT_DARK }}>
            {GROUP_LABELS[progress.topikGroup]} · {LEVEL_LABELS[progress.topikLevel]}
          </span>
          <span style={{ fontSize: '13px', color: '#666' }}>
            누적 {progress.attemptCount}문제 중 {progress.correctCount}개 정답
            {progress.attemptCount > 0 && ` (${Math.round(progress.accuracy * 100)}%)`}
          </span>
        </div>
      )}

      <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
        {loading && <p>불러오는 중...</p>}

        {!loading && noItems && (
          <div>
            <p style={{ color: '#666' }}>아직 출제할 수 있는 문항이 없어요. 등록된 단어로 문항을 만들어볼까요?</p>
            <button
              type="button"
              onClick={handleGenerate}
              disabled={generating}
              style={{
                padding: '10px 18px', cursor: generating ? 'not-allowed' : 'pointer',
                backgroundColor: generating ? '#ccc' : ACCENT, color: 'white', border: 'none', borderRadius: '8px',
              }}
            >
              {generating ? '문항 만드는 중...' : '문항 만들기'}
            </button>
          </div>
        )}

        {!loading && error && !noItems && <p style={{ color: '#dc3545' }}>⚠ {error}</p>}

        {!loading && !noItems && question && (
          <>
            <div style={{ fontSize: '12px', color: '#999', marginBottom: '10px' }}>
              {GROUP_LABELS[question.topikGroup]} · {LEVEL_LABELS[question.topikLevel]}
            </div>
            <div style={{ fontSize: '20px', fontWeight: 700, marginBottom: '20px' }}>
              <ClickableKorean text={question.question} />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '18px' }}>
              {question.options.map((option, idx) => {
                const isSelected = selected === idx
                const isCorrectOption = result && option === result.correctAnswer
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
                      textAlign: 'left', padding: '12px 16px', borderRadius: '8px',
                      border: `1px solid ${borderColor}`, backgroundColor,
                      cursor: selected !== null ? 'default' : 'pointer', fontSize: '15px',
                    }}
                  >
                    <ClickableKorean text={option} />
                  </button>
                )
              })}
            </div>

            {result && (
              <div style={{
                padding: '14px 16px', borderRadius: '10px', marginBottom: '16px',
                backgroundColor: result.correct ? '#f0fdf4' : '#fffbeb',
                border: '1px solid ' + (result.correct ? '#bbf7d0' : '#fde68a'),
              }}>
                <div style={{ fontWeight: 700, marginBottom: '4px' }}>
                  {result.correct ? '✅ 정답이에요!' : `❌ 아쉬워요. 정답: ${result.correctAnswer}`}
                </div>
                {result.levelChanged && (
                  <div style={{ fontSize: '13px', color: '#666' }}>
                    등급이 {GROUP_LABELS[result.currentGroup]} · {LEVEL_LABELS[result.currentLevel]}(으)로 조정됐어요.
                  </div>
                )}
              </div>
            )}

            {result && (
              <button
                type="button"
                onClick={loadNextQuestion}
                style={{
                  width: '100%', padding: '14px', fontSize: '16px', cursor: 'pointer',
                  backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '8px',
                }}
              >
                다음 문제 →
              </button>
            )}
          </>
        )}
      </div>
    </main>
  )
}

export default TopikQuiz
