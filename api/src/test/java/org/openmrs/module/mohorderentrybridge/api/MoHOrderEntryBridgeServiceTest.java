/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.mohorderentrybridge.api;

import static org.junit.Assert.assertNotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.h2.jdbc.JdbcSQLException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openmrs.DrugOrder;
import org.openmrs.Order;
import org.openmrs.Order.Action;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohorderentrybridge.MoHDrugOrder;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;

/**
 * Tests {@link ${MoHOrderEntryBridgeService}}.
 * 
 * Uses OpenMRS 1.11.x API and TEST
 */
public class MoHOrderEntryBridgeServiceTest extends BaseModuleContextSensitiveTest {

	private OrderService orderService = null;
	private ConceptService conceptService = null;
	private EncounterService encounterService = null;
	private ProviderService providerService = null;
	private PatientService patientService = null;
	private MoHOrderEntryBridgeService mohOrderEntryAPIService;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	@Before
	public void setupDatabase() throws Exception {
		orderService = Context.getOrderService();
		patientService = Context.getPatientService();
		providerService = Context.getProviderService();
		encounterService = Context.getEncounterService();
		conceptService = Context.getConceptService();
		mohOrderEntryAPIService = Context.getService(MoHOrderEntryBridgeService.class);
	}

	@Test
	public void shouldSetupContext() {
		assertNotNull(Context.getService(MoHOrderEntryBridgeService.class));
	}

	@Test
	public void testOpenMRSVersion() {// should be 1.11.5 and not 1.6.6
		Assert.assertTrue(OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1.11.5"));
		Assert.assertFalse(OpenmrsConstants.OPENMRS_VERSION_SHORT.startsWith("1.6.6"));
	}

	@Test
	public void theTwoOrderServicesMustBeAccessible() {
		Assert.assertNotNull(orderService);
		Assert.assertNotNull(OrderContextVersion6.getAPI6ContextOrderService());
	}

	@Test(expected = JdbcSQLException.class)
	public void failLoadingAPI6DataSet() throws Exception {
		executeDataSet("DrugOrders-1.6.x-data.xml");
	}

	/**
	 * Tests the assumptions made in upgradeAssumptions.md file
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAlltheMoHUpgradeAssumptions() throws Exception {
		Patient patient2 = patientService.getPatient(2);
		List<Order> patient2Orders = orderService.getAllOrdersByPatient(patient2);
		Integer patient2OrdersOriginalCount = patient2Orders.size();
		Integer patient2OrdersFinalCount = null;
		Order order22 = orderService.getOrder(22);// DISCONTINUED
		DateFormat dFormatter = new SimpleDateFormat("dd/MM/yyyy");
		
		DrugOrder newDrugOrder = new DrugOrder();//All the fields bellow should be set for a new order by MoH modules
		newDrugOrder.setOrderType(orderService.getOrderTypeByUuid("dd3fb1d0-ae06-11e3-a5e2-0800200c9a77"));
		Calendar cal = Calendar.getInstance();
		Calendar cal2 = Calendar.getInstance();
		cal.add(Calendar.DATE, 1);
		cal2.add(Calendar.DATE, 2);// expires the next day
		newDrugOrder.setAction(Action.NEW);
		
		//use this check when saving for start date from the UI
		if(cal.getTime().after(new Date())) {
			//TODO api can't save such a dateactivated or start date
		} else {
			newDrugOrder.setDateActivated(cal.getTime());
		}
		newDrugOrder.setDateCreated(new Date());
		newDrugOrder.setAutoExpireDate(cal2.getTime());
		newDrugOrder.setOrderer(providerService.getProvider(1));
		newDrugOrder.setEncounter(encounterService.getEncounter(6));
		newDrugOrder.setOrderReasonNonCoded("REASON");
		newDrugOrder.setPatient(patient2);
		newDrugOrder.setRoute(conceptService.getConcept(22));
		newDrugOrder.setFrequency(orderService.getOrderFrequency(1));
		newDrugOrder.setDose(2d);
		newDrugOrder.setDoseUnits(conceptService.getConcept(50));
		newDrugOrder.setDrug(conceptService.getDrug(3));
		newDrugOrder.setCareSetting(orderService.getCareSetting(2));
		newDrugOrder.setPreviousOrder(orderService.getOrderByOrderNumber("111"));
		Assert.assertFalse(newDrugOrder.isStarted());
		
		Order savedOrder = orderService.saveOrder(newDrugOrder, null);
		Assert.assertFalse(order22.isActive());// isDiscontinued
		Assert.assertTrue(savedOrder.isActive(cal.getTime()));// is not discontinue
		Assert.assertTrue(savedOrder.isActive(cal2.getTime()));//active on end date
		cal2.add(Calendar.DATE, 3);
		Assert.assertFalse(savedOrder.isActive(cal2.getTime()));//not active after end date
		patient2OrdersFinalCount = orderService.getAllOrdersByPatient(patient2).size();
		Assert.assertTrue(patient2OrdersFinalCount - patient2OrdersOriginalCount == 1);//one order is added after saving
		Assert.assertEquals(savedOrder.getAction(), Action.NEW);
		cal2.add(Calendar.DATE, 30);
		Assert.assertTrue(savedOrder.isStarted());//TODO why is is this order started today when dateActivated is one day later?
		Assert.assertNull(savedOrder.getDateChanged());
		
		String discontinueReason = "Discontine this order";
		Order discontinuedOrder = orderService.discontinueOrder(savedOrder, discontinueReason , new Date(),
				savedOrder.getOrderer(), savedOrder.getEncounter());
		
		Assert.assertNull(savedOrder.getDateChanged());//TODO discontinue action doesn't set date Changed
		Assert.assertEquals(dFormatter.format(savedOrder.getEffectiveStopDate()), dFormatter.format(new Date()));//Date discontinued is date stopped
		Assert.assertTrue(savedOrder.isStarted());
		Assert.assertFalse(discontinuedOrder.isActive());
		Assert.assertFalse(savedOrder.isActive());
		Assert.assertTrue(discontinuedOrder.isExpired());
		Assert.assertEquals(discontinueReason, discontinuedOrder.getOrderReasonNonCoded());
		Assert.assertEquals("REASON", savedOrder.getOrderReasonNonCoded());
		Assert.assertEquals(discontinuedOrder.getAction(), Action.DISCONTINUE);
		Assert.assertEquals(savedOrder.getAction(), Action.NEW);
		Assert.assertEquals(savedOrder.getEffectiveStartDate(), savedOrder.getDateActivated());
		Assert.assertEquals(savedOrder.getEffectiveStopDate(), savedOrder.getDateStopped());
		Assert.assertEquals(discontinuedOrder.getEffectiveStartDate(), discontinuedOrder.getDateActivated());
		Assert.assertNull(discontinuedOrder.getDateStopped());
		Assert.assertNotNull(discontinuedOrder.getAutoExpireDate());
		Assert.assertEquals(discontinuedOrder.getEffectiveStopDate(), discontinuedOrder.getAutoExpireDate());//for a discontinuation order, autoexpire date is set instead of data stopped which is set for a discontinued order, TODO resolve SQL
		
		patient2OrdersFinalCount = orderService.getAllOrdersByPatient(patient2).size();

		Assert.assertTrue(patient2OrdersFinalCount - patient2OrdersOriginalCount == 2);//another order is added when discontinue is invoked
	
		//test deleting an order
		Integer orderId = savedOrder.getOrderId();
		Assert.assertNotNull(orderService.getOrder(orderId));
		orderService.purgeOrder(savedOrder);
		Assert.assertNull(orderService.getOrder(orderId));
	}
	
	@Test
	public void testMoHOrderEntryBridgeAPI() {
		Patient patient2 = patientService.getPatient(2);
		List<MoHDrugOrder> mohDrugOrdersForPatient2 = mohOrderEntryAPIService.getMoHDrugOrdersByPatient(patient2);
		
		Assert.assertSame(mohDrugOrdersForPatient2.size(), mohOrderEntryAPIService.getDrugOrdersByPatient(patient2).size());
		for(MoHDrugOrder mohDOrder : mohDrugOrdersForPatient2) {
			if(mohDOrder.getIsActive()) {
				DrugOrder dOrder = mohDOrder.getDrugOrder();
				
				Assert.assertSame(mohDOrder.getIsActive(), dOrder.isActive());
				//TODO may be; go deeper into testing isActive
			}
		}
	}
}
