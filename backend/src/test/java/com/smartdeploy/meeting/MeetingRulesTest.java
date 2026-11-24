package com.smartdeploy.meeting;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Rendez-vous : le format iCalendar et l'ordre des créneaux. */
class MeetingRulesTest {

    @Test
    void specialCharactersAreEscapedForCalendars() {
        assertEquals("Point\\, panne\\; suite\\nligne", MeetingService.escape("Point, panne; suite\nligne"));
    }

    @Test
    void theSlotsAreReturnedInTheirOrder() {
        Meeting m = new Meeting();
        LocalDateTime a = LocalDateTime.of(2026, 11, 25, 10, 0);
        m.setSlot1(a);
        m.setSlot2(a.plusDays(1));
        assertEquals(2, MeetingService.slots(m).size());
        assertEquals(a, MeetingService.slots(m).get(0));
    }
}
