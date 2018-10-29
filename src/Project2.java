/*
Author: Tiffany Do
10/28/2018
This project simulates a hotel that had 25 guests come in for the night.
The hotel employs 2 front desk employees to check in employees, assign rooms, and distribute keys.
The hotel also employs 2 bellhops that receive bags and deliver bags to guests if a guest has more than 2 bags.
*/

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.Random;

public class Project2
{
	//semaphores
	private static Semaphore checkin = new Semaphore(2, true);
	private static Semaphore guest_rdy_checkin = new Semaphore(0, true);
	private static Semaphore mutex1 = new Semaphore(1, true);
	private static Semaphore mutex2 = new Semaphore(1, true);
	private static Semaphore room = new Semaphore(0, true);
	private static Semaphore bellhop_rdy = new Semaphore(2, true);
	private static Semaphore guest_rdy_bellhop = new Semaphore(0, true);
	private static Semaphore bags = new Semaphore(0, true);
	private static Semaphore[] inRoom = new Semaphore[25];
	private static Semaphore[] tip = new Semaphore[] {new Semaphore(0, true), new Semaphore(0,true)};

	//initialize array of semaphores
	static
	{
		for(int i = 0; i < 25; i++)
		{
			inRoom[i] = new Semaphore(0, true);
		}
	}

	//for random number of bags
	private static Random rand = new Random();

	//lists and arrays to store data between threads

	//queues for guests at the front desk and for bellhops
	private static ArrayList<Integer> guestAtDesk = new ArrayList<>();
	private static ArrayList<Integer> guestAtBellhop = new ArrayList<>();

	//store what room each customer is in and who helps the customers
	private static int[] customerRooms = new int[25];
	private static int[] customerHelped = new int[25];
	private static int[] customerHelpedBellhop = new int[25];

	//keep track of how many guests have retired and the next assignable room
	private static int guestsDone = 0;
	private static int currentAvailableRoom = 0;


	public static void main(String[] args)
	{
		//hard limit on number of guests, front desks, and bellhops
		int numGuests = 25;
		int numFrontDesk = 2;
		int numBellhops = 2;

		//initialize guests, frontdesk, and bellhops
		Thread guests[] = new Thread[numGuests];
		Thread frontdesk[] = new Thread[numFrontDesk];
		Thread bellhops[] = new Thread[numBellhops];

		System.out.println("Simulation begins");

		//initialize all threads and start them
		for(int i = 0; i < numFrontDesk; i++)
		{
			frontdesk[i] = new Thread(new FrontDesk(i));
			System.out.println("Front desk employee " + i + " created");
			frontdesk[i].start();
			delay();
		}

		for(int i = 0; i < numBellhops; i++)
		{
			bellhops[i] = new Thread(new Bellhop(i));
			System.out.println("Bellhop " + i + " created");
			bellhops[i].start();
			delay();
		}

		for(int i = 0; i < numGuests; i++)
		{
			guests[i] = new Thread(new Guest(i));
			System.out.println("Guest " + i + " created");
			delay();
		}


		for(int i = 0; i < numGuests; i++)
		{
			guests[i].start();
		}

		//wait for all guests to join
		for(int i = 0; i < numGuests; ++i )
		{
			try
			{
				guests[i].join();
				System.out.println("Guest " + i + " joined");
			}
			catch (InterruptedException e) { }
		}

		//block until all guests are done
		while(true)
		{
			if(guestsDone == numGuests)
				break;
		}
		//end simulation
		System.out.println("Simulation ends");
		System.exit(0);

	}

	//delays threads so output is cleaner
	public static void delay()
	{
		try
		{
			Thread.sleep(20);
		}
		catch (InterruptedException e) { }
	}

	//Guest class
	public static class Guest implements Runnable
	{
		//each guest has a unique ID and amount of bags
		int id;
		int numBags;
		Guest(int i)
		{
			id = i;
			numBags = rand.nextInt((5)+1);
		}

		public void run()
		{
			try
			{
				System.out.println("Guest " + id + " enters hotel with " + numBags + " bags");
				//mutual exclusion to ensure that only one guest enters the queue at a time
				mutex1.acquire();
				guestAtDesk.add(id);
				guest_rdy_checkin.release();
				mutex1.release();
				checkin.acquire();
				room.acquire();
				System.out.println("Guest " + id + " receives room key for room " + customerRooms[id] + " from front desk employee " + customerHelped[id]);

				//this guest does not require a bellhop
				if(numBags <= 2)
				{
					System.out.println("Guest " + id + " enters room " + customerRooms[id]);
					System.out.println("Guest " + id + " retires for the evening");
				}
				//the guest requires a bellhop
				else
				{
					System.out.println("Guest " + id + " requests for help with bags");
					mutex2.acquire();
					guestAtBellhop.add(id);
					guest_rdy_bellhop.release();
					mutex2.release();
					bellhop_rdy.acquire();
					//waits for bellhop to get all the bags
					bags.acquire();
					System.out.println("Guest " + id + " enters room " + customerRooms[id]);
					//signals to bellhop that they are in the room
					inRoom[id].release();
					//waits for bellhop to deliver bags so they can tip
					int bellHop = customerHelpedBellhop[id];
					tip[bellHop].acquire();
					System.out.println("Guest " + id + " receives bags from bellhop " + bellHop + " and gives tip");
					System.out.println("Guest " + id + " retires for the evening");
				}
				guestsDone++;
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}

	//Front desk employee class
	public static class FrontDesk implements Runnable
	{
		int id;
		FrontDesk(int i)
		{
			id = i;
		}

		public void run()
		{
			while(true)
			{
				try
				{
					//wait for a guest to be ready
					guest_rdy_checkin.acquire();
					//mutual exclusion to ensure that only one guest is dequeued at a time
					mutex1.acquire();
					int guestID = guestAtDesk.remove(0);
					currentAvailableRoom++;
					System.out.println("Front Desk employee " + id + " registers guest " + guestID + " and assigns room " + currentAvailableRoom);
					//assign customers rooms and who helped them
					customerRooms[guestID] = currentAvailableRoom;
					customerHelped[guestID] = id;
					mutex1.release();
					room.release();
					checkin.release();
				}
				catch(InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	//Bellhop class
	public static class Bellhop implements Runnable
	{
		int id;
		Bellhop(int i)
		{
			id = i;
		}

		public void run()
		{
			while(true)
			{
				try
				{
					//wait for guests to be ready for a bellhop
					guest_rdy_bellhop.acquire();
					//mutual exclusion to ensure that only one guest is dequeued at a time
					mutex2.acquire();
					int guestID = guestAtBellhop.remove(0);
					System.out.println("Bellhop " + id + " receives bags from guest " + guestID);
					customerHelpedBellhop[guestID] = id;
					mutex2.release();
					//bellhop signals to guest that they have received all the bags
					bags.release();
					//waits for guest to be in their room
					inRoom[guestID].acquire();
					System.out.println("Bellhop " + id + " delivers bags to guest " + guestID);
					//signals to the guest that they have delivered all bags
					tip[id].release();
					bellhop_rdy.release();
				}
				catch(InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
