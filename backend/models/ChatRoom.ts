export interface IChatRoom {
  id: number; // Sequential auto-increment ID
  roomName: string;
  participantName: string;
  participantFlag: string;
  participantRank: string;
  participantTerritory: string;
  lastMessage: string;
  lastMessageTime: number;
  isActive: boolean;
  isWaiting: boolean;
}
