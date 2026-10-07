// 학습 유형 진단 화면의 영어 보조표기. 문항/유형은 서버가 한국어로 내려주므로 코드(A1~E4, 유형명)를 키로 번역을 붙인다.

export const AREA_EN = {
  A: 'Lifestyle & focus patterns',
  B: 'Information-processing preferences',
  C: 'Motivation & self-efficacy',
  D: 'Digital tool skills',
  E: 'Study strategy & metacognition',
}

export const SCALE_EN = ['Strongly disagree', 'Disagree', 'Neutral', 'Agree', 'Strongly agree']

export const QUESTION_EN = {
  A1: 'I concentrate best within an hour of waking up in the morning.',
  A2: 'I find it easy to study with focus for 2 hours or more at a time.',
  A3: 'I am easily disturbed by noise or my surroundings when I study.',
  A4: 'I make a study plan and follow it.',
  B1: 'I understand much faster when there are visuals such as pictures, charts or color marks.',
  B2: 'I remember things well when I listen to lectures or explanations.',
  B3: 'Studying by taking notes or summarizing myself works well for me.',
  B4: 'I memorize better when I imitate or say things out loud while studying.',
  C1: 'I study much harder when my target score (level) is clear.',
  C2: 'I feel more motivated studying with someone than studying alone.',
  C3: 'When I meet a hard problem, I try to solve it instead of giving up.',
  C4: 'I am confident in managing my own planned amount of study.',
  D1: 'Using phone apps or online lectures is more comfortable for me than paper textbooks.',
  D2: 'When a phone notification arrives while I study, I check it right away.',
  D3: 'I am good at finding and using study information on YouTube or social media.',
  D4: 'I concentrate better with printed paper textbooks than with digital devices.',
  E1: 'I have a habit of analyzing wrong answers and keeping a wrong-answer notebook.',
  E2: 'Before studying, I decide concretely what I will learn today and how.',
  E3: 'I know exactly what my weak areas are (listening / reading / writing / vocabulary).',
  E4: 'I always set aside time to review and check what I have studied.',
}

export const TYPE_EN = {
  STRATEGIC_ANALYST: {
    label: 'Strategic Analyst',
    description: 'A self-directed learner who plans well and analyzes mistakes.',
    studyTip: 'Go through past exams 5 times, turn weak areas into data, and focus on writing feedback.',
  },
  VISUAL_IMMERSIVE: {
    label: 'Visual Immersive',
    description: 'You understand best when you learn with visuals like pictures, charts and colors.',
    studyTip: 'Strengthen visual memory with diagrams, mind maps and color-coded vocabulary.',
  },
  AUDITORY_EMPATHETIC: {
    label: 'Auditory Empathetic',
    description: 'You learn best by listening to lectures and explanations, and are motivated by outside stimulation.',
    studyTip: 'Shadowing, dictation and instant audio feedback from one-on-one lessons work well.',
  },
  EXPERIENTIAL_ACTOR: {
    label: 'Experiential Actor',
    description: 'You learn by solving, writing and speaking yourself, and need instant feedback.',
    studyTip: 'Repeat the Pomodoro method (25 min focus, 5 min break) with instant grading and feedback.',
  },
  ADAPTIVE_MIXED: {
    label: 'Adaptive Mixed',
    description: 'A self-directed learner who flexibly switches study methods depending on the situation.',
    studyTip: 'Rotate methods through the week (Mon video, Tue notes, Wed listening) and keep a self-check routine.',
  },
  SNS_DEPENDENT: {
    label: 'SNS Dependent',
    description: 'You are motivated by short-form content, peer learning and outside stimulation.',
    studyTip: 'Block phone notifications while studying, and use study-group mock exams and daily check-ins.',
  },
}
