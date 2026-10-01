// 한국어 조사(을/를, 은/는, 으로/로)를 명사 끝 글자의 받침 유무에 따라 골라준다.
// "모의고사를"/"모의고사로"처럼 사용자가 선택한 값(관심사, 메뉴 이름 등)을 문장에
// 끼워 넣을 때, 받침 여부를 미리 알 수 없어서 조사를 잘못 붙이는 문제를 막는다.
const HAS_BATCHIM = (word) => {
  if (!word) return false
  const code = word.charCodeAt(word.length - 1)
  // 한글 음절(가~힣) 범위를 벗어나면(영문/숫자 등) 받침이 있는 것처럼 취급해 "이/을/으로"를 쓴다.
  if (code < 0xac00 || code > 0xd7a3) return true
  return (code - 0xac00) % 28 !== 0
}

export function withEulReul(word) {
  return `${word}${HAS_BATCHIM(word) ? '을' : '를'}`
}

export function withEunNeun(word) {
  return `${word}${HAS_BATCHIM(word) ? '은' : '는'}`
}

// "으로" 또는 "로"만 돌려준다(이미 따옴표 등으로 감싼 값 뒤에 조사만 붙이고 싶을 때 사용).
export function euroRoSuffix(word) {
  // 받침이 'ㄹ'이면 "로"를 쓴다(예: "서울로"). 그 외 받침이 있으면 "으로".
  const code = word ? word.charCodeAt(word.length - 1) : 0
  const isRieul = code >= 0xac00 && code <= 0xd7a3 && (code - 0xac00) % 28 === 8
  return HAS_BATCHIM(word) && !isRieul ? '으로' : '로'
}

export function withEuroRo(word) {
  return `${word}${euroRoSuffix(word)}`
}
