package com.josiahcooper.miningstats;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InventoryEtaTest
{
	@Test
	public void zeroRateReturnsNoEstimate()
	{
		assertEquals(-1L, InventoryEta.etaSeconds(10, 0.0));
		assertEquals(-1L, InventoryEta.etaSeconds(10, -50.0));
	}

	@Test
	public void noFreeSlotsReturnsNoEstimate()
	{
		assertEquals(-1L, InventoryEta.etaSeconds(0, 600.0));
		assertEquals(-1L, InventoryEta.etaSeconds(-3, 600.0));
	}

	@Test
	public void typicalRateProducesExpectedEta()
	{
		// 10 free slots at 600 ores/hr = 6000ms/ore = 60s
		assertEquals(60L, InventoryEta.etaSeconds(10, 600.0));
		// 14 free slots at 300 ores/hr = 12000ms/ore = 168s
		assertEquals(168L, InventoryEta.etaSeconds(14, 300.0));
	}

	@Test
	public void formatNegativeReturnsEmDash()
	{
		assertEquals("—", InventoryEta.formatEta(-1));
		assertEquals("—", InventoryEta.formatEta(-9999));
	}

	@Test
	public void formatSubMinuteShowsSeconds()
	{
		assertEquals("0s", InventoryEta.formatEta(0));
		assertEquals("45s", InventoryEta.formatEta(45));
		assertEquals("59s", InventoryEta.formatEta(59));
	}

	@Test
	public void formatMinuteShowsMinutesAndSeconds()
	{
		assertEquals("1m 0s", InventoryEta.formatEta(60));
		assertEquals("2m 5s", InventoryEta.formatEta(125));
		assertEquals("59m 59s", InventoryEta.formatEta(3599));
	}

	@Test
	public void formatHourShowsHoursAndMinutes()
	{
		assertEquals("1h 0m", InventoryEta.formatEta(3600));
		assertEquals("1h 2m", InventoryEta.formatEta(3725));
		assertEquals("2h 30m", InventoryEta.formatEta(9000));
	}

	@Test
	public void inventoryCapacityConstantIsTwentyEight()
	{
		assertEquals(28, InventoryEta.INVENTORY_CAPACITY);
		assertTrue(InventoryEta.INVENTORY_CAPACITY > 0);
	}
}
