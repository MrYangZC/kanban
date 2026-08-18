import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { BoardView } from '@/api/types'

export const useBoardStore = defineStore('board', () => {
  const board = ref<BoardView | null>(null)

  function setBoard(b: BoardView) {
    board.value = b
  }

  function clear() {
    board.value = null
  }

  return { board, setBoard, clear }
})
