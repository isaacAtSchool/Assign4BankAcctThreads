package com.isaac;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ParentsAndBanks {

    private double getRandomCash() {
        final int MAXCASH = 10000000;
        // add a minimum of 1 for the function
        return ThreadLocalRandom.current().nextDouble(0.01, MAXCASH);
    }

    public class Parent extends Thread {
        final BankAccount acct;
        String name;

        // all a parent can really do is add money to the bank account
        public Parent(BankAccount acct) {
            this.acct = acct;
            this.name = "Parent";
        }

        public Parent(BankAccount acct, String name) {
            this.acct = acct;
            this.name = name;
        }

        private synchronized void depositMoney(BankAccount acct, double amount) {

            synchronized (this.acct) {
                try {
                    acct.getSemaphore().acquire();
                    acct.deposit(amount);
                    acct.getSemaphore().release();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void run() {
            // repeatedly add random amounts of money to the linked bank account

            synchronized (this.acct) {
                double amount = getRandomCash();
                depositMoney(this.acct, amount);
                String msg = this.name + " added money to account. New balance is: " + acct.getBalance();
                System.out.println(msg);
            }
        }

    }

    public class Kid extends Thread {
        final BankAccount acct;
        String name;
        // all a kid can really do is take money from the bank account

        public Kid(BankAccount acct) {
            this.acct = acct;
            this.name = "Kid";
        }

        public Kid(BankAccount acct, String name) {
            this.acct = acct;
            this.name = name;
        }

        private synchronized void takeMoney(BankAccount acct, double amount) {
            synchronized (this.acct) {
                try {
                    acct.getSemaphore().acquire();
                    try {
                        System.out.println(this.name + " wants ice cream money!");
                        String msg1;
                        acct.withdraw(amount);
                        String amtString = NumberFormat.getCurrencyInstance().format(new BigDecimal(Double.toString(amount)));
                        msg1 = this.name + " took " + amtString + " in ice cream money. New balance is " + acct.getBalance();
                        System.out.println(msg1);
                    } catch (BankAccount.WithdrawException e) {
                        System.out.println("Withdraw Error for " + this.name);
                        e.printInsufficientFunds();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    acct.getSemaphore().release();
                }
            }
        }

        public void run() {
            double amount = getRandomCash();
            takeMoney(this.acct, amount);
        }
    }

    public class BankAccount extends Thread {
        Semaphore sem;
        BigDecimal balance;

        public BankAccount() {
            //create a binary semaphore that we will use like a mutex
            this.sem = new Semaphore(1);
            // we care about two digits to the right of the decimal
            this.balance = new BigDecimal("0").setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        public synchronized Semaphore getSemaphore() {
            return sem;
        }

        public synchronized void deposit(double amount) {
            // the add method returns a BigDecimal with the same scale as this.balance
            balance = balance.add(new BigDecimal(Double.toString(amount)));
        }

        public synchronized void withdraw(double amount) throws WithdrawException {
            BigDecimal bigAmount = new BigDecimal(Double.toString(amount));
            // insufficent funds
            if (balance.compareTo(bigAmount) < 0) {
                throw new WithdrawException(amount);
            } else {
                balance = balance.subtract(bigAmount);
            }
        }

        public synchronized String getBalance() {
            return NumberFormat.getCurrencyInstance().format(balance);
        }

        class WithdrawException extends Exception {
            double amount;
            BigDecimal store;

            public WithdrawException(double amount) {
                this.store = new BigDecimal(Double.toString(amount));
            }

            public BigDecimal getAmount() {
                return store;
            }

            public String getAmountString() {
                return NumberFormat.getCurrencyInstance().format(store);
            }

            public void printInsufficientFunds() {
                // although I should be printing to system.err, this makes the ouput confusing to read
                System.out.println("Can not withdraw " + getAmountString() + " from " + getBalance());
            }
        }
    }

    // todo add initializer for dynamic variables
    // get num kids

    //
    public static void main(String[] args) {
        /* initialize basic runtime parameters */
        int numKids = 1;
        int payPeriod = 1;
        int eatPeriod = 1;
        int runDuration;
        Scanner sc = new Scanner(System.in);
        System.out.println("How many kids do you want? -> ");
        numKids = sc.nextInt();
        System.out.println("How often do the parents make money? (1 day = 100 milliseconds)");
        payPeriod = sc.nextInt();
        System.out.println("How often do the kids want ice cream? (1 day = 100 milliseconds)");
        eatPeriod = sc.nextInt();
        System.out.println("How many seconds do you want the program to last?");
        runDuration = sc.nextInt();

        ParentsAndBanks parentsAndBanks = new ParentsAndBanks();



        BankAccount bank = parentsAndBanks.new BankAccount();
        Parent mom = parentsAndBanks.new Parent(bank, "Mom");
        Parent dad = parentsAndBanks.new Parent(bank, "Dad");

        // prepare the thread executor service with a pool of numKids + 2 assignable threads
        // to run tasks in
        ScheduledExecutorService scheduledEx = Executors.newScheduledThreadPool(numKids + 2);

        int delay = 0;
        int payInterval = payPeriod*100; //unit is specified in scheduled execution method call
//      ThreadLocalRandom.current().nextInt(300, 3000)
        scheduledEx.scheduleAtFixedRate(mom, delay, payInterval, MILLISECONDS);
        scheduledEx.scheduleAtFixedRate(dad, delay, payInterval, MILLISECONDS);

        // execute the kids

        int eatInterval = eatPeriod*100;
        for (int i = 0; i < numKids; i++) {

            scheduledEx.scheduleAtFixedRate(parentsAndBanks.new Kid(bank, "Kid" + Integer.toString(i + 1)), delay, eatInterval, MILLISECONDS);
        }


//         shutdown after 10 seconds of running
        TimerTask shutdownTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println("shutting down program ");
                System.exit(0);
            }
        };
        Timer timer = new Timer("send shutdown signal");
        timer.schedule(shutdownTask, runDuration *1000);

    }
}
