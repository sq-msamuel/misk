import { IActionType } from "./types"

export interface IAction<T, P> {
  readonly type: T
  readonly payload?: P
}

export function createActionTypes (base: string, actions: string[] = []): IActionType {
  return actions.reduce((acc: IActionType, type: string) => {
    acc[type] = `${base}_${type}`
    return acc
  }, {})
}

export function createAction<T extends string, P>(type: T, payload: P): IAction<T,P> {
  return {type, payload}
}

export const errorMessage = (error: any) => {
  if (!error) {
    return ""
  }

  let code = error.errorCode
  if (!code) {
    code = error.response && error.response.status === 401
      ? "Unauthorized"
      : "InternalServerError"
  }

  return code
}
