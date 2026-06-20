import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IPost } from '../models/Post';
import { IComment } from '../models/Comment';
import { getNextSequenceValue } from '../models/Counter';
import { IUser, calculateUserRank } from '../models/User';
import { createNotificationDirectly } from './notificationController';

export function normalizeBackendPost(raw: any): IPost {
  if (!raw) {
    return {
      id: 0,
      authorId: "anonymous",
      authorName: "Anonymous Citizen",
      authorUsername: "@anonymous",
      authorRank: "Citizen",
      authorTerritory: "Global",
      authorFlag: "🌍",
      content: "",
      category: "General",
      timestamp: Date.now(),
      knowledgeValue: 0,
      contributionProof: 0,
      reputationImpact: 100,
      reactedWiseUsers: "",
      reactedHelpfulUsers: "",
      reactedInspiringUsers: ""
    };
  }

  // 1. Coerce ID to valid number
  let cleanId = 0;
  if (raw.id !== undefined && raw.id !== null) {
    if (typeof raw.id === 'number') {
      cleanId = raw.id;
    } else {
      const parsedStr = String(raw.id).replace(/\D/g, '');
      const parsed = parseInt(parsedStr, 10);
      cleanId = isNaN(parsed) ? 999 + Math.floor(Math.random() * 1000) : parsed;
    }
  } else if (raw._id !== undefined && raw._id !== null) {
    if (typeof raw._id === 'number') {
      cleanId = raw._id;
    } else {
      const parsedStr = String(raw._id).replace(/\D/g, '');
      const parsed = parseInt(parsedStr, 10);
      cleanId = isNaN(parsed) ? 999 + Math.floor(Math.random() * 1000) : parsed;
    }
  }

  // 2. Map snake_case to camelCase
  const authorId = raw.authorId || raw.author_id || "anonymous";
  const authorName = raw.authorName || raw.author_name || "Anonymous Citizen";
  const authorUsername = raw.authorUsername || raw.author_username || "@anonymous";
  const authorRank = raw.authorRank || raw.author_rank || "Citizen";
  const authorTerritory = raw.authorTerritory || raw.author_territory || "Global";
  const authorFlag = raw.authorFlag || raw.author_flag || "🌍";

  const content = raw.content || raw.title || "";
  const category = raw.category || raw.type || "Inquiry";

  // Handle timestamp
  let timestamp = Date.now();
  if (raw.timestamp !== undefined && raw.timestamp !== null) {
    timestamp = Number(raw.timestamp);
  } else if (raw.created_at !== undefined && raw.created_at !== null) {
    const val = Number(raw.created_at);
    if (val < 100000000) {
      timestamp = Date.now() - (100000000 - val) * 1000;
    } else {
      timestamp = val;
    }
  }

  // Metrics
  const knowledgeValue = Number(raw.knowledgeValue !== undefined ? raw.knowledgeValue : (raw.wise_count || 0));
  const contributionProof = Number(raw.contributionProof !== undefined ? raw.contributionProof : (raw.helpful_count || 0));
  
  let reputationImpact = Number(raw.reputationImpact !== undefined ? raw.reputationImpact : 100);
  if (raw.reputationImpact === undefined && raw.inspiring_count !== undefined) {
    reputationImpact = 100 + Number(raw.inspiring_count);
  }

  const reactedWiseUsers = raw.reactedWiseUsers || raw.reacted_wise_users || "";
  const reactedHelpfulUsers = raw.reactedHelpfulUsers || raw.reacted_helpful_users || "";
  const reactedInspiringUsers = raw.reactedInspiringUsers || raw.reacted_inspiring_users || "";

  const returnedPost: IPost = {
    id: cleanId,
    authorId,
    authorName,
    authorUsername,
    authorRank,
    authorTerritory,
    authorFlag,
    content,
    category,
    timestamp,
    knowledgeValue,
    contributionProof,
    reputationImpact,
    reactedWiseUsers,
    reactedHelpfulUsers,
    reactedInspiringUsers
  };

  if (raw.royalSignature) {
    returnedPost.royalSignature = raw.royalSignature;
  }

  return returnedPost;
}

export const getPosts = async (req: Request, res: Response) => {
  try {
    const { category } = req.query;
    const db = getFirestoreDb();
    
    // Fetch all posts and sort in memory to avoid requiring manual Firestore composite indexes
    const snapshot = await db.collection('posts').get();
    let posts = snapshot.docs.map(doc => normalizeBackendPost(doc.data()));
    
    if (category) {
      posts = posts.filter(p => p.category === String(category));
    }
    
    posts.sort((a, b) => b.timestamp - a.timestamp);
    res.status(200).json(posts);
  } catch (error: any) {
    console.error("Firestore getPosts Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const createPost = async (req: Request, res: Response) => {
  try {
    const postData = req.body;
    const db = getFirestoreDb();
    
    const nextId = await getNextSequenceValue('post_id');
    const newPost: IPost = {
      id: nextId,
      authorId: postData.authorId || "anonymous",
      authorName: postData.authorName || "Anonymous Citizen",
      authorUsername: postData.authorUsername || "@anonymous",
      authorRank: postData.authorRank || "Citizen",
      authorTerritory: postData.authorTerritory || "Global",
      authorFlag: postData.authorFlag || "🌍",
      content: postData.content || "",
      category: postData.category || "General",
      timestamp: Date.now(),
      knowledgeValue: 0,
      contributionProof: 0,
      reputationImpact: Number(postData.reputationImpact) || 100,
      reactedWiseUsers: "",
      reactedHelpfulUsers: "",
      reactedInspiringUsers: ""
    };

    // Autofill royal signature for valid Monarch authors
    if (newPost.authorId && newPost.authorId !== "anonymous") {
      const userSnap = await db.collection('users').doc(newPost.authorId).get();
      if (userSnap.exists) {
        const userData = userSnap.data();
        const isMonarch = userData?.currentRank === 'King' || userData?.currentRank === 'Queen' || userData?.royalTitle === 'King' || userData?.royalTitle === 'Queen';
        if (isMonarch && userData?.royalSignatureEnabled !== false) {
          newPost.royalSignature = {
            monarchTitle: userData.currentRank || userData.royalTitle || "Monarch",
            monarchName: userData.name || "Sovereign",
            signedAt: Date.now()
          };
        }
      }
    }

    // Store in collection using string of sequential ID as doc key
    await db.collection('posts').doc(String(nextId)).set(newPost);
    res.status(201).json(normalizeBackendPost(newPost));
  } catch (error: any) {
    console.error("Firestore createPost Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const reactToPost = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const { userId, reactionType } = req.body;
    
    if (!userId || !reactionType) {
       res.status(400).json({ error: "userId and reactionType are required." });
       return;
    }

    const db = getFirestoreDb();
    const actorId = (req as any).userId || userId;
    const cleanUserId = String(actorId).trim();
    const cleanType = String(reactionType).toLowerCase().trim();

    let reactedField: 'reactedWiseUsers' | 'reactedHelpfulUsers' | 'reactedInspiringUsers' = 'reactedWiseUsers';
    let counterField: 'knowledgeValue' | 'contributionProof' | 'reputationImpact' = 'knowledgeValue';
    let creditsAward = { kb: 0, cb: 0 };

    if (cleanType.includes('wise') || cleanType.includes('🧠')) {
      reactedField = 'reactedWiseUsers';
      counterField = 'knowledgeValue';
      creditsAward = { kb: 2, cb: 0 };
    } else if (cleanType.includes('helpful') || cleanType.includes('🔥') || cleanType.includes('🤝')) {
      reactedField = 'reactedHelpfulUsers';
      counterField = 'contributionProof';
      creditsAward = { kb: 0, cb: 2 };
    } else {
      reactedField = 'reactedInspiringUsers';
      counterField = 'reputationImpact';
      creditsAward = { kb: 1, cb: 1 };
    }

    let finalPostResult: IPost | null = null;

    let notifyRecipient: string | null = null;
    let notifySender: string | null = null;

    // Run custom dynamic atomic Transaction (Bug #5)
    await db.runTransaction(async (transaction) => {
      let postDocRef = db.collection('posts').doc(String(postId));
      let postDoc = await transaction.get(postDocRef);

      if (!postDoc.exists) {
        const query = await db.collection('posts').where('id', '==', Number(postId)).get();
        if (query.empty) {
          throw new Error("Post not found.");
        }
        postDocRef = query.docs[0].ref;
        postDoc = await transaction.get(postDocRef);
      }

      const post = postDoc.data() as IPost;
      const currentReactedStr = post[reactedField] || "";
      let reactedList = currentReactedStr.split(',').map(s => s.trim()).filter(Boolean);

      let isAdded = false;
      if (reactedList.includes(cleanUserId)) {
        reactedList = reactedList.filter(id => id !== cleanUserId);
        post[counterField] = Math.max(0, post[counterField] - 1);
      } else {
        reactedList.push(cleanUserId);
        post[counterField] = (post[counterField] || 0) + 1;
        isAdded = true;
      }

      post[reactedField] = reactedList.join(',');

      // Choose rewarded history field based on reactedField to prevent double credit allocation
      const rewardedField = reactedField === 'reactedWiseUsers' ? 'rewardedWiseUsers' :
                            reactedField === 'reactedHelpfulUsers' ? 'rewardedHelpfulUsers' :
                            'rewardedInspiringUsers';

      const currentRewardedStr = (post as any)[rewardedField] || "";
      let rewardedList = currentRewardedStr.split(',').map((s: string) => s.trim()).filter(Boolean);

      let awardCredits = false;
      if (isAdded) {
        if (!rewardedList.includes(cleanUserId)) {
          rewardedList.push(cleanUserId);
          (post as any)[rewardedField] = rewardedList.join(',');
          awardCredits = true;
        }
      }

      // If award is earned, fetch dynamic author update details BEFORE writing anything
      let authorToUpdate: any = null;
      let authorDocRef: any = null;
      if (isAdded && awardCredits && post.authorId) {
        const authorIdClean = post.authorId.toLowerCase().trim().replace(/^@/, '');
        authorDocRef = db.collection('users').doc(authorIdClean);
        let authorDoc = await transaction.get(authorDocRef);
        
        if (!authorDoc.exists) {
          // Query by email/username
          const emailQuery = await db.collection('users').where('email', '==', authorIdClean).get();
          if (!emailQuery.empty) {
            authorDocRef = emailQuery.docs[0].ref;
            authorDoc = await transaction.get(authorDocRef);
          } else {
            const uQuery = await db.collection('users').where('username', '==', `@${authorIdClean}`).get();
            if (!uQuery.empty) {
              authorDocRef = uQuery.docs[0].ref;
              authorDoc = await transaction.get(authorDocRef);
            }
          }
        }

        if (authorDoc.exists) {
          authorToUpdate = authorDoc.data() as IUser;
          authorToUpdate.knowledgeCredits = (authorToUpdate.knowledgeCredits || 0) + creditsAward.kb;
          authorToUpdate.contributionCredits = (authorToUpdate.contributionCredits || 0) + creditsAward.cb;
          authorToUpdate.reputationScore = Math.min(100, (authorToUpdate.reputationScore || 98) + 1);
          authorToUpdate.currentRank = calculateUserRank(authorToUpdate.knowledgeCredits, authorToUpdate.contributionCredits);
        }
      }

      // NOW PERFORM ALL atomic writes (sets) together after all transaction reads
      transaction.set(postDocRef, post);
      if (authorToUpdate && authorDocRef) {
        transaction.set(authorDocRef, authorToUpdate);
      }

      // Save reaction notification details to be triggered after successful transaction
      const postAuthorId = post.authorId || (post as any).author_id;
      if (isAdded && postAuthorId && postAuthorId.toLowerCase().trim() !== cleanUserId.toLowerCase().trim()) {
        notifyRecipient = postAuthorId;
        notifySender = cleanUserId;
      }

      finalPostResult = post;
    });

    if (finalPostResult) {
      // Trigger notification after transaction successfully commits to avoid lockups
      if (notifyRecipient && notifySender) {
        try {
          const userDoc = await db.collection('users').doc(notifySender!).get();
          const userName = userDoc.exists ? (userDoc.data() as any).name : "Someone";
          
          console.log(`[DEBUG REACTION NOTIFICATION] reactionType: ${reactionType}, postId: ${postId}, postAuthorId: ${notifyRecipient}, reactorId: ${notifySender}, recipientId: ${notifyRecipient}`);
          console.log("[DEBUG REACTION NOTIFICATION] executing createNotificationDirectly()...");

          await createNotificationDirectly(
            notifyRecipient!,
            notifySender!,
            "reaction",
            "New Reaction on your Post",
            `${userName} reacted with ${reactionType} to your post.`
          );
        } catch (e) {
          console.error("Reaction notification error:", e);
        }
      }
      res.status(200).json(normalizeBackendPost(finalPostResult));
    } else {
      res.status(400).json({ error: "Atomic transaction failed to finalize." });
    }
  } catch (error: any) {
    console.error("Firestore reactToPost Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const deletePost = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const db = getFirestoreDb();
    
    // Attempt doc delete
    await db.collection('posts').doc(String(postId)).delete();

    // In case doc key is different, query and delete
    const query = await db.collection('posts').where('id', '==', Number(postId)).get();
    for (const doc of query.docs) {
      await doc.ref.delete();
    }

    res.status(200).json({ success: true });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getComments = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const db = getFirestoreDb();
    
    const snapshot = await db.collection('comments').where('postId', '==', Number(postId)).get();
    const comments = snapshot.docs.map(doc => doc.data() as IComment);
    
    // Sort in memory chronologically
    comments.sort((a, b) => a.timestamp - b.timestamp);
    res.status(200).json(comments);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const addComment = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const commentData = req.body;
    const db = getFirestoreDb();
    
    const nextId = await getNextSequenceValue('comment_id');
    const comment: IComment = {
      id: nextId,
      postId: Number(postId),
      authorName: commentData.authorName || "Anonymous",
      authorFlag: commentData.authorFlag || "🌍",
      authorRank: commentData.authorRank || "Citizen",
      content: commentData.content || "",
      timestamp: Date.now()
    };

    await db.collection('comments').doc(String(nextId)).set(comment);

    try {
      // Trigger comment notification with robust post lookup (handles custom doc ID structures)
      let postSnapshot = await db.collection('posts').doc(String(postId)).get();
      if (!postSnapshot.exists) {
        const query = await db.collection('posts').where('id', '==', Number(postId)).get();
        if (!query.empty) {
          postSnapshot = query.docs[0] as any;
        }
      }
      if (postSnapshot && postSnapshot.exists) {
        const post = postSnapshot.data() as IPost;
        const postAuthorId = post.authorId || (post as any).author_id;
        const commentAuthorEmail = (req as any).userId;
        
        console.log(`[DEBUG COMMENT NOTIFICATION] commentAuthor: ${commentAuthorEmail}, postAuthor: ${postAuthorId}, recipientId: ${postAuthorId}`);
        
        if (!commentAuthorEmail) {
          console.error("[COMMENT NOTIFICATION] No userId found in request");
        } else if (!postAuthorId) {
          console.error("[COMMENT NOTIFICATION] No postAuthorId found for post ID " + postId);
        } else if (postAuthorId.toLowerCase().trim() !== commentAuthorEmail.toLowerCase().trim()) {
          console.log("[DEBUG COMMENT NOTIFICATION] executing createNotificationDirectly()...");
          await createNotificationDirectly(
            postAuthorId,
            commentAuthorEmail,
            "comment",
            "New Comment on your Post",
            `${comment.authorName} commented: "${comment.content.substring(0, 40)}${comment.content.length > 40 ? '...' : ''}"`
          );
        }
      }
    } catch (notifErr) {
      console.error("Comment notification failure:", notifErr);
    }

    res.status(201).json(comment);
  } catch (error: any) {
    console.error("Firestore addComment Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const editComment = async (req: Request, res: Response) => {
  try {
    const { postId, commentId } = req.params;
    const { content } = req.body;
    const db = getFirestoreDb();
    const docRef = db.collection('comments').doc(String(commentId));
    let docSnap = await docRef.get();
    
    if (docSnap.exists) {
      await docRef.update({ content, edited: true, timestamp: Date.now() });
      res.status(200).json({ success: true });
    } else {
      const query = await db.collection('comments')
        .where('postId', '==', Number(postId))
        .where('id', '==', Number(commentId))
        .get();
      if (!query.empty) {
        await query.docs[0].ref.update({ content, edited: true, timestamp: Date.now() });
        res.status(200).json({ success: true });
      } else {
        res.status(404).json({ error: "Comment not found" });
      }
    }
  } catch (error: any) {
    console.error("Firestore editComment Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const deleteComment = async (req: Request, res: Response) => {
  try {
    const { postId, commentId } = req.params;
    const db = getFirestoreDb();
    const docRef = db.collection('comments').doc(String(commentId));
    let docSnap = await docRef.get();
    
    if (docSnap.exists) {
      await docRef.delete();
      res.status(200).json({ success: true });
    } else {
      const query = await db.collection('comments')
        .where('postId', '==', Number(postId))
        .where('id', '==', Number(commentId))
        .get();
      if (!query.empty) {
        await query.docs[0].ref.delete();
        res.status(200).json({ success: true });
      } else {
        res.status(404).json({ error: "Comment not found" });
      }
    }
  } catch (error: any) {
    console.error("Firestore deleteComment Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const editPost = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const { content } = req.body;
    const db = getFirestoreDb();
    const docRef = db.collection('posts').doc(String(postId));
    let docSnap = await docRef.get();
    
    if (docSnap.exists) {
      await docRef.update({ content, edited: true });
      const updatedSnap = await docRef.get();
      res.status(200).json(normalizeBackendPost(updatedSnap.data()));
    } else {
      const query = await db.collection('posts').where('id', '==', Number(postId)).get();
      if (!query.empty) {
        await query.docs[0].ref.update({ content, edited: true });
        const updatedSnap = await query.docs[0].ref.get();
        res.status(200).json(normalizeBackendPost(updatedSnap.data()));
      } else {
        res.status(404).json({ error: "Post not found" });
      }
    }
  } catch (error: any) {
    console.error("Firestore editPost Error:", error);
    res.status(500).json({ error: error.message });
  }
};
