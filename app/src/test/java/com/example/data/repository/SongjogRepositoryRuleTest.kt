package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DirectoryUser
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class SongjogRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_andDirectory_createsAndObservesSuccessfully() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@songjog.test")
        val repo = SongjogRepository(firestore, auth)
        val user = checkNotNull(auth.currentUser)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserSignedInProfile(user, "bn")
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMyProfile(aliceUid).first { it != null }
        }
        assertEquals(aliceUid, profile?.userId)

        val directory = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeDirectoryUsers().first { list -> list.any { it.userId == aliceUid } }
        }
        assertTrue(directory.any { it.userId == aliceUid })
    }

    @Test
    fun chatAndMessages_participantCanSendAndReact_nonParticipantDenied() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val aliceUid = signInTestUser("alice_$suffix@songjog.test")
        val bobUid = signInTestUser("bob_$suffix@songjog.test")
        val charlieUid = signInTestUser("charlie_$suffix@songjog.test")

        signInTestUser("alice_$suffix@songjog.test")
        val aliceRepo = SongjogRepository(firestore, auth)
        aliceRepo.ensureUserSignedInProfile(checkNotNull(auth.currentUser), "bn")

        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getOrCreateDirectChat(
                currentUser = UserProfile(userId = aliceUid, displayName = "Alice"),
                targetUser = DirectoryUser(userId = bobUid, displayName = "Bob")
            )
        }

        val chats = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChats(aliceUid).first { list -> list.any { it.id == chatId } }
        }
        val chat = chats.first { it.id == chatId }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.sendMessage(
                chat = chat,
                senderName = "Alice",
                type = "text",
                text = "আসসালামালাইকুম!"
            )
        }

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { it.isNotEmpty() }
        }
        assertEquals("আসসালামালাইকুম!", messages.first().text)

        // Charlie (non-participant) cannot read the chat
        signInTestUser("charlie_$suffix@songjog.test")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("chats").document(chatId).get().await()
            }
            fail("Expected PERMISSION_DENIED for non-participant Charlie ($charlieUid)")
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
        }
    }

    @Test
    fun statusesAndCalls_authenticatedUserCanPostAndObserve() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val aliceUid = signInTestUser("alice_st_$suffix@songjog.test")
        val bobUid = signInTestUser("bob_st_$suffix@songjog.test")

        signInTestUser("alice_st_$suffix@songjog.test")
        val repo = SongjogRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.postStatus(
                userName = "Alice",
                userAvatar = null,
                type = "text",
                text = "সংযোগ অ্যাপ দারুণ!",
                backgroundHex = "#0B7A5C"
            )
        }
        val statuses = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeActiveStatuses().first { list -> list.any { it.userId == aliceUid } }
        }
        assertTrue(statuses.any { it.text == "সংযোগ অ্যাপ দারুণ!" })

        val callId = "call_$suffix"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createCallOffer(
                callId = callId,
                callerName = "Alice",
                callerAvatar = null,
                calleeId = bobUid,
                calleeName = "Bob",
                calleeAvatar = null,
                callType = "audio",
                offerSdp = "v=0..."
            )
        }
        val calls = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCalls(aliceUid).first { list -> list.any { it.id == callId } }
        }
        assertEquals("ringing", calls.first { it.id == callId }.status)
    }

    @Test
    fun observeChats_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = SongjogRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("chats")
                    .whereArrayContains("participantIds", "anyone")
                    .get()
                    .await()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val ffe = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
        }
        // Also ensure repo fail-fast throws when unauthenticated
        try {
            repo.observeChats("anyone").first()
            fail("Expected IllegalStateException for unauthenticated repo call")
        } catch (_: IllegalStateException) {
            // Expected
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
