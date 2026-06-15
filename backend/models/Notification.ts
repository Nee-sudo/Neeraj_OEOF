export interface INotification {
  id: string;
  recipientId: string;
  senderId: string;
  type: string; // "message", "comment", "reaction", "profile_view", "friend_request"
  title: string;
  body: string;
  isRead: boolean;
  createdAt: number;
}
