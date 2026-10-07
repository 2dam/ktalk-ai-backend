import { createContext, useContext } from 'react'

// 화면 문구 아래에 옅은 영어 보조표기를 보여줄지 여부. 한글을 못 읽는 초급 학습자가
// 메뉴·제목·버튼 뜻을 짐작할 수 있게 하는 장치로, 기본은 켜짐이고 헤더의 EN 버튼으로 끌 수 있다.
export const EnglishHintContext = createContext({ enabled: true, toggle: () => {} })

export function useEnglishHint() {
  return useContext(EnglishHintContext)
}
