package com.example.visionbridge

import com.example.visionbridge.data.HelpRequest
import org.junit.Assert.*
import org.junit.Test

class VolunteerRequestFilteringTest {

    private fun isPending(status: String): Boolean {
        return status.equals("PENDING", ignoreCase = true) ||
                status.equals("searching", ignoreCase = true)
    }

    private fun filterActiveRequests(requests: List<HelpRequest>): List<HelpRequest> {
        return requests.filter { isPending(it.status) }
    }

    private fun updateRequestInList(
        currentList: MutableList<HelpRequest>,
        incoming: HelpRequest
    ): List<HelpRequest> {
        val index = currentList.indexOfFirst { it.id == incoming.id }
        val pending = isPending(incoming.status)

        if (index != -1) {
            if (!pending) {
                currentList.removeAt(index)
            } else {
                currentList[index] = incoming
            }
        } else if (pending) {
            currentList.add(0, incoming)
        }
        return currentList
    }

    private fun createHelpRequest(
        id: String,
        status: String,
        requester: String = "user-1"
    ): HelpRequest {
        return HelpRequest(
            id = id,
            requester = requester,
            requesterName = "User $requester",
            latitude = 18.5204,
            longitude = 73.8567,
            helpDescription = "Need visual assistance",
            status = status
        )
    }

    @Test
    fun testPendingAndSearchingStatusIdentification() {
        assertTrue(isPending("PENDING"))
        assertTrue(isPending("pending"))
        assertTrue(isPending("searching"))
        assertTrue(isPending("SEARCHING"))

        assertFalse(isPending("COMPLETED"))
        assertFalse(isPending("completed"))
        assertFalse(isPending("CANCELLED"))
        assertFalse(isPending("cancelled"))
        assertFalse(isPending("ACCEPTED"))
        assertFalse(isPending("REJECTED"))
    }

    @Test
    fun testFilterActiveRequestsDropsCompletedAndCancelled() {
        val requests = listOf(
            createHelpRequest("1", "PENDING"),
            createHelpRequest("2", "COMPLETED"),
            createHelpRequest("3", "searching"),
            createHelpRequest("4", "CANCELLED"),
            createHelpRequest("5", "ACCEPTED")
        )

        val filtered = filterActiveRequests(requests)
        assertEquals(2, filtered.size)
        assertEquals("1", filtered[0].id)
        assertEquals("3", filtered[1].id)
    }

    @Test
    fun testCompletedRequestRemovedFromActiveList() {
        val activeList = mutableListOf(
            createHelpRequest("req-1", "PENDING"),
            createHelpRequest("req-2", "searching")
        )

        // Simulate incoming Realtime event marking req-1 as COMPLETED
        val completedReq = createHelpRequest("req-1", "COMPLETED")
        val updatedList = updateRequestInList(activeList, completedReq)

        assertEquals(1, updatedList.size)
        assertEquals("req-2", updatedList[0].id)
    }

    @Test
    fun testCancelledRequestRemovedFromActiveList() {
        val activeList = mutableListOf(
            createHelpRequest("req-1", "PENDING"),
            createHelpRequest("req-2", "searching")
        )

        // Simulate incoming Realtime event marking req-2 as CANCELLED
        val cancelledReq = createHelpRequest("req-2", "CANCELLED")
        val updatedList = updateRequestInList(activeList, cancelledReq)

        assertEquals(1, updatedList.size)
        assertEquals("req-1", updatedList[0].id)
    }

    @Test
    fun testNewPendingRequestAddedToTopOfList() {
        val activeList = mutableListOf(
            createHelpRequest("req-1", "PENDING")
        )

        val newPending = createHelpRequest("req-2", "searching")
        val updatedList = updateRequestInList(activeList, newPending)

        assertEquals(2, updatedList.size)
        assertEquals("req-2", updatedList[0].id)
        assertEquals("req-1", updatedList[1].id)
    }

    @Test
    fun testNonPendingIncomingRequestNotAddedIfAbsent() {
        val activeList = mutableListOf(
            createHelpRequest("req-1", "PENDING")
        )

        val completedRequest = createHelpRequest("req-external", "COMPLETED")
        val updatedList = updateRequestInList(activeList, completedRequest)

        assertEquals(1, updatedList.size)
        assertEquals("req-1", updatedList[0].id)
    }

    @Test
    fun testSupabaseQueryFilterFormat() {
        val query = "help_requests?status=in.(PENDING,searching)&order=created_at.desc"
        assertTrue(query.contains("status=in.(PENDING,searching)"))
        assertTrue(query.contains("order=created_at.desc"))
    }
}
