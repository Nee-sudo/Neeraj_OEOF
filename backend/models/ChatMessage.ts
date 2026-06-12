export interface IChatMessage {
  id: number; // Sequential auto-increment ID
  roomId: number;
  senderId: string;
  senderName: string;
  messageText: string;
  timestamp: number;
}
