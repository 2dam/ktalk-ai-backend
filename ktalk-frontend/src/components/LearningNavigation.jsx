import { useEffect, useState } from 'react'
import axios from 'axios'
import { AI_URL } from '../api'
import { TAB_COLORS } from '../theme'
import AssessmentSurvey from './AssessmentSurvey'
import ContentManager from './ContentManager'
import ClipAndLearn from './ClipAndLearn'
import CharacterChat from './CharacterChat'
import PronunciationCoach from './PronunciationCoach'
import PersonalizedLearning from './PersonalizedLearning'
import ReviewAlarm from './ReviewAlarm'

const ACCENT = TAB_COLORS.navigation.accent
const ACCENT_DARK = TAB_COLORS.navigation.dark
const ACCENT_TINT = TAB_COLORS.navigation.tint

const SUGGESTED_INTERESTS = ['축구', 'K-POP', '드라마', '게임', '요리', '여행', '영화', '반려동물']

const STAGES = [
  { id: 'interest', label: '관심사 찾기' },
  { id: 'infer', label: '유추 연습' },
  { id: 'pattern', label: '패턴 응용' },
  { id: 'sensory', label: '언어 감각' },
  { id: 'review', label: '복습 알람' },
  { id: 'done', label: '완료' },
]

const SENSORY_TARGET_REPEATS = 5

function speak(text) {
  if (!('speechSynthesis' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'ko-KR'
  utterance.rate = 0.85
  window.speechSynthesis.speak(utterance)
}

function StageTracker({ stage }) {
  const currentIndex = STAGES.findIndex((s) => s.id === stage)
  return (
    <div style={{ display: 'flex', gap: '6px', marginBottom: '20px' }}>
      {STAGES.map((s, idx) => (
        <div key={s.id} style={{ flex: 1, textAlign: 'center' }}>
          <div
            style={{
              height: '6px',
              borderRadius: '3px',
              backgroundColor: idx <= currentIndex ? ACCENT : '#eee',
              marginBottom: '6px',
              transition: 'background-color 0.3s',
            }}
          />
          <span style={{ fontSize: '11px', color: idx <= currentIndex ? ACCENT_DARK : '#999', fontWeight: idx === currentIndex ? 700 : 400 }}>
            {s.label}
          </span>
        </div>
      ))}
    </div>
  )
}

function EmptyLessonNotice({ onStart }) {
  return (
    <div style={{
      padding: '16px', borderRadius: '8px', backgroundColor: '#fff7ed',
      border: '1px solid #fde68a', marginBottom: '16px', textAlign: 'left',
    }}>
      <p style={{ margin: '0 0 10px' }}>아직 시작한 학습이 없어요. 관심사를 먼저 골라야 이 단계의 문장/패턴이 준비돼요.</p>
      <button
        type="button"
        onClick={onStart}
        style={{
          padding: '8px 16px', cursor: 'pointer', backgroundColor: ACCENT, color: 'white',
          border: 'none', borderRadius: '999px', fontSize: '13px',
        }}
      >
        관심사 고르러 가기 →
      </button>
    </div>
  )
}

function PracticeTool({ id, title, openTool, setOpenTool, children }) {
  const isOpen = openTool === id
  return (
    <details
      open={isOpen}
      onToggle={(e) => setOpenTool(e.target.open ? id : null)}
      style={{ marginTop: '16px', border: '1px solid #eee', borderRadius: '8px', padding: '14px 16px' }}
    >
      <summary style={{ cursor: 'pointer', fontWeight: 700, color: ACCENT_DARK }}>{title}</summary>
      {isOpen && <div style={{ marginTop: '16px' }}>{children}</div>}
    </details>
  )
}

function LearningNavigation({ target }) {
  const [stage, setStage] = useState('interest')
  const [openTool, setOpenTool] = useState(null)
  const [interest, setInterest] = useState('')
  const [lesson, setLesson] = useState(null)
  const [isGenerating, setIsGenerating] = useState(false)
  const [error, setError] = useState('')

  // 유추 연습 단계
  const [guess, setGuess] = useState('')
  const [hintCount, setHintCount] = useState(0)
  const [revealedMeaning, setRevealedMeaning] = useState(false)

  // 패턴 응용(티칭백) 단계
  const [studentExplanation, setStudentExplanation] = useState('')
  const [studentExample, setStudentExample] = useState('')
  const [teachBackFeedback, setTeachBackFeedback] = useState(null)
  const [isEvaluating, setIsEvaluating] = useState(false)

  // 언어 감각 단계
  const [repeatCount, setRepeatCount] = useState(0)

  // 복습 알람: 지금 복습할 문장 개수 (앱 내 알람 배지)
  const [dueCount, setDueCount] = useState(0)

  const fetchDueCount = async () => {
    if (!hasToken()) {
      setDueCount(0)
      return
    }
    try {
      const res = await axios.get(`${REVIEW_URL}/count`, { headers: authHeaders() })
      setDueCount(res.data?.data?.dueCount ?? 0)
    } catch {
      // 알람 배지 조회는 실패해도 조용히 무시
    }
  }

  useEffect(() => {
    fetchDueCount()
  }, [])

  // 외부(홈 히어로, TOPIK 페이지, 가격표 등)에서 특정 도구로 바로 이동해달라는
  // 요청이 오면, 이제는 별도 탭이 아니라 그 도구가 속한 단계로 이동하고 해당
  // 도구 패널을 펼쳐서 보여준다.
  useEffect(() => {
    if (!target) return
    setStage(target.stage)
    if (target.tool) setOpenTool(target.tool)
  }, [target])

  const resetAll = () => {
    setStage('interest')
    setOpenTool(null)
    setInterest('')
    setLesson(null)
    setError('')
    setGuess('')
    setHintCount(0)
    setRevealedMeaning(false)
    setStudentExplanation('')
    setStudentExample('')
    setTeachBackFeedback(null)
    setRepeatCount(0)
  }

  const handleStart = async (topic) => {
    const chosen = (topic ?? interest).trim()
    if (!chosen) {
      setError('관심사를 입력하거나 골라주세요.')
      return
    }

    setInterest(chosen)
    setIsGenerating(true)
    setError('')
    try {
      const response = await axios.post(`${AI_URL}/guided-learning/generate`, { interest: chosen }, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
      })
      setLesson(response.data)
      setGuess('')
      setHintCount(0)
      setRevealedMeaning(false)
      setStage('infer')
    } catch (err) {
      setError(err.response?.data?.message || 'AI가 문장을 만들지 못했어요. Gemini API 키가 설정되어 있는지 확인해주세요.')
    } finally {
      setIsGenerating(false)
    }
  }

  const handleSubmitTeachBack = async () => {
    if (!studentExplanation.trim()) {
      setError('패턴을 어떻게 설명할지 먼저 적어주세요.')
      return
    }
    setIsEvaluating(true)
    setError('')
    try {
      const response = await axios.post(`${AI_URL}/guided-learning/teach-back`, {
        pattern: lesson.pattern,
        patternExplanation: lesson.patternExplanation,
        studentExplanation,
        studentExample,
      }, {
        headers: { 'Content-Type': 'application/json; charset=utf-8' },
      })
      setTeachBackFeedback(response.data)
    } catch (err) {
      setError(err.response?.data?.message || '선생님 피드백을 받지 못했어요.')
    } finally {
      setIsEvaluating(false)
    }
  }

  return (
    <div>
      <div style={{ padding: '20px', border: '2px solid ' + ACCENT, borderRadius: '8px', backgroundColor: ACCENT_TINT, marginBottom: '20px' }}>
        <h2>🧭 Learning Navigation</h2>
        <p style={{ margin: 0 }}>
          관심사에서 시작해 유추 → 패턴 응용 → 언어 감각 훈련까지, 스스로 찾아가는 4단계 학습 흐름이에요.
        </p>
      </div>

      <StageTracker stage={stage} />

      {dueCount > 0 && stage !== 'review' && (
        <div
          role="button"
          tabIndex={0}
          onClick={() => setStage('review')}
          onKeyDown={(e) => { if (e.key === 'Enter') setStage('review') }}
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px',
            padding: '12px 16px', borderRadius: '10px', cursor: 'pointer', marginBottom: '16px',
            backgroundColor: ACCENT_TINT, border: '1px solid ' + ACCENT,
          }}
        >
          <span style={{ fontWeight: 700, color: ACCENT_DARK }}>
            🔔 복습할 문장이 {dueCount}개 있어요
          </span>
          <span style={{ color: ACCENT_DARK, fontSize: '14px' }}>지금 복습하기 →</span>
        </div>
      )}

      {error && (
        <p style={{ color: '#dc3545', padding: '10px 15px', backgroundColor: '#fff5f5', borderRadius: '8px' }}>
          ⚠ {error}
        </p>
      )}

      {stage === 'interest' && (
        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
          <h3 style={{ fontSize: '22px', marginTop: 0 }}>너의 관심은 뭐니?</h3>
          <p style={{ color: '#666' }}>무엇이든 좋아요. 그 주제로 된 한국어 문장을 만들어드릴게요.</p>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '16px' }}>
            {SUGGESTED_INTERESTS.map((topic) => (
              <button
                key={topic}
                type="button"
                onClick={() => handleStart(topic)}
                disabled={isGenerating}
                style={{
                  padding: '8px 16px', borderRadius: '999px', border: '1px solid ' + ACCENT,
                  backgroundColor: '#fff', color: ACCENT_DARK, cursor: isGenerating ? 'not-allowed' : 'pointer', fontSize: '14px',
                }}
              >
                {topic}
              </button>
            ))}
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <input
              type="text"
              value={interest}
              onChange={(e) => setInterest(e.target.value)}
              placeholder="직접 입력해도 좋아요 (예: 우주, 축구선수 이름...)"
              style={{ flex: 1, padding: '12px', fontSize: '16px' }}
              disabled={isGenerating}
              onKeyDown={(e) => { if (e.key === 'Enter') handleStart() }}
            />
            <button
              type="button"
              onClick={() => handleStart()}
              disabled={isGenerating}
              style={{
                padding: '12px 24px', fontSize: '16px', cursor: isGenerating ? 'not-allowed' : 'pointer',
                backgroundColor: isGenerating ? '#ccc' : ACCENT, color: 'white', border: 'none', borderRadius: '4px',
              }}
            >
              {isGenerating ? '만드는 중...' : '시작하기 →'}
            </button>
          </div>

          <PracticeTool id="assessment" title="🧠 아직 내 학습 유형을 모른다면? 진단부터 해보기" openTool={openTool} setOpenTool={setOpenTool}>
            <AssessmentSurvey />
          </PracticeTool>
        </div>
      )}

      {stage === 'infer' && (
        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
          {!lesson && <EmptyLessonNotice onStart={() => setStage('interest')} />}
          {lesson && (
          <>
          <div style={{ fontSize: '12px', color: '#999', marginBottom: '8px' }}>
            관심사: {lesson.interest} · 이 문장은 무슨 뜻일까요?
          </div>

          <div style={{
            fontSize: '24px', fontWeight: 700, padding: '20px', borderRadius: '12px',
            backgroundColor: ACCENT_TINT, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '10px',
          }}>
            {lesson.sentence}
            <button
              type="button"
              onClick={() => speak(lesson.sentence)}
              style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '20px' }}
              aria-label="문장 듣기"
            >
              🔊
            </button>
          </div>

          <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
            내가 생각하는 뜻
          </label>
          <textarea
            value={guess}
            onChange={(e) => setGuess(e.target.value)}
            placeholder="뜻을 자유롭게 추측해서 적어보세요..."
            style={{ width: '100%', padding: '10px', fontSize: '15px', minHeight: '70px', marginBottom: '14px' }}
          />

          {hintCount > 0 && (
            <div style={{ marginBottom: '14px' }}>
              {lesson.hints.slice(0, hintCount).map((hint, idx) => (
                <div key={idx} style={{
                  padding: '10px 14px', borderRadius: '8px', backgroundColor: '#fff7ed',
                  border: '1px solid ' + ACCENT_TINT, marginBottom: '6px', fontSize: '14px',
                }}>
                  💡 힌트 {idx + 1}: {hint}
                </div>
              ))}
            </div>
          )}

          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '16px' }}>
            <button
              type="button"
              onClick={() => setHintCount((c) => Math.min(c + 1, lesson.hints.length))}
              disabled={hintCount >= lesson.hints.length}
              style={{
                padding: '8px 16px', cursor: hintCount >= lesson.hints.length ? 'not-allowed' : 'pointer',
                backgroundColor: '#fff', color: ACCENT_DARK, border: '1px solid ' + ACCENT, borderRadius: '999px', fontSize: '13px',
              }}
            >
              힌트 보기 ({hintCount}/{lesson.hints.length})
            </button>
            <button
              type="button"
              onClick={() => setRevealedMeaning(true)}
              style={{
                padding: '8px 16px', cursor: 'pointer', backgroundColor: '#fff', color: '#555',
                border: '1px solid #ccc', borderRadius: '999px', fontSize: '13px',
              }}
            >
              정답 확인하기
            </button>
          </div>

          {revealedMeaning && (
            <div style={{ padding: '16px', borderRadius: '12px', backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', marginBottom: '16px' }}>
              <div style={{ fontWeight: 700, marginBottom: '8px' }}>정답: {lesson.meaning}</div>
              <div style={{ fontSize: '13px', color: '#666', display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                {lesson.vocab?.map((item, idx) => (
                  <span key={idx} style={{ padding: '4px 10px', backgroundColor: '#fff', borderRadius: '999px', border: '1px solid #ddd' }}>
                    {item.word} = {item.meaning}
                  </span>
                ))}
              </div>
            </div>
          )}

          <button
            type="button"
            onClick={() => setStage('pattern')}
            disabled={!revealedMeaning}
            style={{
              width: '100%', padding: '14px', fontSize: '16px', cursor: revealedMeaning ? 'pointer' : 'not-allowed',
              backgroundColor: revealedMeaning ? ACCENT : '#ccc', color: 'white', border: 'none', borderRadius: '8px',
            }}
          >
            다음: 패턴 응용하기 →
          </button>
          </>
          )}

          <PracticeTool id="contents" title="📚 내가 만든 콘텐츠로 유추 연습 더 하기" openTool={openTool} setOpenTool={setOpenTool}>
            <ContentManager />
          </PracticeTool>
          <PracticeTool id="clip" title="🎬 유튜브 클립에서 표현 찾아 유추하기" openTool={openTool} setOpenTool={setOpenTool}>
            <ClipAndLearn />
          </PracticeTool>
        </div>
      )}

      {stage === 'pattern' && (
        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px' }}>
          {!lesson && <EmptyLessonNotice onStart={() => setStage('interest')} />}
          {lesson && (
          <>
          <h3 style={{ marginTop: 0 }}>이제 당신이 선생님이에요 🧑‍🏫</h3>
          <p style={{ color: '#666' }}>방금 배운 문장의 패턴을 다른 사람에게 설명한다고 생각하고 적어보세요.</p>

          <div style={{ padding: '14px', borderRadius: '8px', backgroundColor: ACCENT_TINT, marginBottom: '16px', fontSize: '15px' }}>
            {lesson.sentence} <span style={{ color: '#999', fontSize: '13px' }}>({lesson.meaning})</span>
          </div>

          <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
            이 문장에서 쓰인 패턴을 내 말로 설명하기
          </label>
          <textarea
            value={studentExplanation}
            onChange={(e) => setStudentExplanation(e.target.value)}
            placeholder="예: 이 문장은 얼마나 자주 무언가를 하는지 말할 때 쓰는 것 같아요..."
            style={{ width: '100%', padding: '10px', fontSize: '15px', minHeight: '80px', marginBottom: '14px' }}
          />

          <label style={{ fontSize: '13px', color: '#999', display: 'block', marginBottom: '4px' }}>
            같은 패턴으로 나만의 문장 만들기 (선택)
          </label>
          <input
            type="text"
            value={studentExample}
            onChange={(e) => setStudentExample(e.target.value)}
            placeholder="같은 패턴을 응용한 내 문장..."
            style={{ width: '100%', padding: '10px', fontSize: '15px', marginBottom: '16px' }}
          />

          {!teachBackFeedback ? (
            <button
              type="button"
              onClick={handleSubmitTeachBack}
              disabled={isEvaluating}
              style={{
                width: '100%', padding: '14px', fontSize: '16px', cursor: isEvaluating ? 'not-allowed' : 'pointer',
                backgroundColor: isEvaluating ? '#ccc' : ACCENT, color: 'white', border: 'none', borderRadius: '8px',
              }}
            >
              {isEvaluating ? '선생님이 확인하는 중...' : '선생님께 확인받기'}
            </button>
          ) : (
            <>
              <div style={{
                padding: '16px', borderRadius: '12px', marginBottom: '16px',
                backgroundColor: teachBackFeedback.patternUnderstood ? '#f0fdf4' : '#fffbeb',
                border: '1px solid ' + (teachBackFeedback.patternUnderstood ? '#bbf7d0' : '#fde68a'),
              }}>
                <div style={{ fontWeight: 700, marginBottom: '6px' }}>
                  {teachBackFeedback.patternUnderstood ? '✅ 선생님 피드백' : '📝 선생님 피드백'}
                </div>
                <div style={{ fontSize: '14px', lineHeight: 1.6 }}>{teachBackFeedback.feedback}</div>
              </div>
              <details style={{ marginBottom: '16px', fontSize: '13px', color: '#666' }}>
                <summary style={{ cursor: 'pointer' }}>참고: 원래 패턴 설명 보기</summary>
                <div style={{ marginTop: '8px', padding: '10px', backgroundColor: '#f9f9f9', borderRadius: '8px' }}>
                  <b>{lesson.pattern}</b>
                  <div style={{ marginTop: '4px' }}>{lesson.patternExplanation}</div>
                </div>
              </details>
              <button
                type="button"
                onClick={() => setStage('sensory')}
                style={{
                  width: '100%', padding: '14px', fontSize: '16px', cursor: 'pointer',
                  backgroundColor: ACCENT, color: 'white', border: 'none', borderRadius: '8px',
                }}
              >
                다음: 언어 감각 훈련하기 →
              </button>
            </>
          )}
          </>
          )}

          <PracticeTool id="chat" title="💬 AI와 실전 회화로 패턴 응용해보기" openTool={openTool} setOpenTool={setOpenTool}>
            <CharacterChat />
          </PracticeTool>
        </div>
      )}

      {stage === 'sensory' && (
        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px', textAlign: 'center' }}>
          {!lesson && <EmptyLessonNotice onStart={() => setStage('interest')} />}
          {lesson && (
          <>
          <h3 style={{ marginTop: 0 }}>언어 감각 훈련</h3>
          <p style={{ color: '#666' }}>{lesson.sensoryImagery}</p>

          <div style={{
            fontSize: '40px', fontWeight: 800, color: ACCENT_DARK, margin: '20px 0',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '14px',
          }}>
            {lesson.sensoryWord}
            <button
              type="button"
              onClick={() => speak(lesson.sensoryWord)}
              style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '30px' }}
              aria-label="단어 듣기"
            >
              🔊
            </button>
          </div>

          <p style={{ fontSize: '14px', color: '#666', marginBottom: '20px' }}>{lesson.sensoryInstruction}</p>

          <div style={{ height: '8px', backgroundColor: '#f3f4f6', borderRadius: '4px', marginBottom: '10px', overflow: 'hidden' }}>
            <div style={{
              height: '100%', width: `${Math.min(repeatCount / SENSORY_TARGET_REPEATS, 1) * 100}%`,
              backgroundColor: ACCENT, transition: 'width 0.3s',
            }} />
          </div>
          <div style={{ fontSize: '13px', color: '#999', marginBottom: '20px' }}>
            {Math.min(repeatCount, SENSORY_TARGET_REPEATS)} / {SENSORY_TARGET_REPEATS}번 소리 내어 말했어요
          </div>

          <button
            type="button"
            onClick={() => { speak(lesson.sensoryWord); setRepeatCount((c) => c + 1) }}
            style={{
              padding: '16px 32px', fontSize: '18px', cursor: 'pointer', backgroundColor: ACCENT,
              color: 'white', border: 'none', borderRadius: '999px', marginBottom: '16px',
            }}
          >
            🗣 소리 내어 말했어요
          </button>

          <div>
            <button
              type="button"
              onClick={() => setStage('review')}
              disabled={repeatCount < SENSORY_TARGET_REPEATS}
              style={{
                width: '100%', padding: '14px', fontSize: '16px',
                cursor: repeatCount < SENSORY_TARGET_REPEATS ? 'not-allowed' : 'pointer',
                backgroundColor: repeatCount < SENSORY_TARGET_REPEATS ? '#ccc' : ACCENT_DARK,
                color: 'white', border: 'none', borderRadius: '8px',
              }}
            >
              다음: 복습 알람 →
            </button>
          </div>
          </>
          )}

          <div style={{ textAlign: 'left' }}>
            <PracticeTool id="pronunciation" title="🎙 발음 코치에게 확인받기" openTool={openTool} setOpenTool={setOpenTool}>
              <PronunciationCoach />
            </PracticeTool>
            <PracticeTool id="personalized" title="🔁 개인화 복습으로 반복하기" openTool={openTool} setOpenTool={setOpenTool}>
              <PersonalizedLearning onNavigate={() => { setStage('infer'); setOpenTool('contents') }} />
            </PracticeTool>
          </div>
        </div>
      )}

      {stage === 'review' && (
        <ReviewAlarm
          justLearned={lesson}
          onComplete={() => { fetchDueCount(); setStage('done') }}
        />
      )}

      {stage === 'done' && !lesson && (
        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px', textAlign: 'center' }}>
          <div style={{ fontSize: '48px', marginBottom: '10px' }}>🎉</div>
          <h3 style={{ marginTop: 0 }}>복습을 마쳤어요!</h3>
          <button
            type="button"
            onClick={resetAll}
            style={{
              padding: '14px 28px', fontSize: '16px', cursor: 'pointer', backgroundColor: ACCENT,
              color: 'white', border: 'none', borderRadius: '8px',
            }}
          >
            새로운 관심사로 학습 시작하기
          </button>
        </div>
      )}

      {stage === 'done' && lesson && (
        <div style={{ border: '1px solid #ddd', borderRadius: '8px', padding: '24px', textAlign: 'center' }}>
          <div style={{ fontSize: '48px', marginBottom: '10px' }}>🎉</div>
          <h3 style={{ marginTop: 0 }}>'{lesson.interest}'(으)로 한 학습을 마쳤어요!</h3>
          <div style={{ textAlign: 'left', padding: '16px', backgroundColor: ACCENT_TINT, borderRadius: '12px', marginBottom: '20px' }}>
            <div style={{ marginBottom: '8px' }}><b>문장:</b> {lesson.sentence} ({lesson.meaning})</div>
            <div style={{ marginBottom: '8px' }}><b>패턴:</b> {lesson.pattern}</div>
            <div><b>오늘의 감각 단어:</b> {lesson.sensoryWord}</div>
          </div>
          <button
            type="button"
            onClick={resetAll}
            style={{
              padding: '14px 28px', fontSize: '16px', cursor: 'pointer', backgroundColor: ACCENT,
              color: 'white', border: 'none', borderRadius: '8px',
            }}
          >
            새로운 관심사로 다시 시작하기
          </button>
        </div>
      )}
    </div>
  )
}

export default LearningNavigation
