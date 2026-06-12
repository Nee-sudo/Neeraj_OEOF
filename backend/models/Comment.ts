export interface IComment {
  id: number; // Sequential auto-increment ID
  postId: number;
  authorName: string;
  authorFlag: string;
  authorRank: string;
  content: string;
  timestamp: number;
}
