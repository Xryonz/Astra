export interface PushPayload {
  title: string
  body:  string
  url?:  string
  icon?: string
  tag?:  string

  renotify?: boolean

  actionable?: boolean

  channelId?: string

  dmConvId?:  string

  sender?: string
}

export async function sendPush(userId: string, payload: PushPayload) {

  void import('./fcm').then(({ sendFcmToUser }) => sendFcmToUser(userId, payload)).catch(() => {})
}
