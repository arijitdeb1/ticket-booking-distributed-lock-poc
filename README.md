# 🎟️ Ticket Booking Distributed Lock POC

## 🚀 Overview

This project demonstrates how to prevent **duplicate ticket booking** in a high-concurrency environment using **Redis-based distributed locking**.

It implements two approaches:

* 🔴 **Redisson (Single Redis Instance)** — simple, production-ready locking
* 🔐 **Redlock (Multi-Redis Nodes)** — quorum-based distributed locking for higher fault tolerance

---

## 🛠️ Getting Started

To run this project locally, refer to **[SETUP.md](SETUP.md)** for step-by-step instructions covering:

* Starting Redis via Docker
* Building and running the Spring Boot application
* Testing the API (booking + payment flows)
* Simulating concurrent requests

---

## 💡 Problem Statement

In real-world systems (like Ticketmaster), multiple users or retries can attempt to book the **same seat simultaneously**, leading to:

* Duplicate bookings ❌
* Inconsistent state ❌
* Payment issues ❌

---

## ✅ Solution Approach

This POC combines:

* **Distributed Locks** → Ensure only one request processes a seat at a time
* **Idempotency Keys** → Ensure safe retries without duplicate execution

---

## ⚙️ Key Highlights

* Prevents concurrent booking of the same seat
* Demonstrates **race condition handling**
* Shows **safe lock acquisition & release**
* Includes **watchdog-based lock renewal (via Redisson)**
* Compares **single-node vs multi-node locking strategies**

---

## 🧠 What You’ll Learn

* How Redis distributed locks work under the hood
* Why atomic operations (Lua) are critical
* Difference between Redisson and Redlock
* How to design **idempotent and fault-tolerant systems**

---

## 🧪 Use Case

> Simulate multiple users trying to book the same ticket → only one succeeds, others fail gracefully.

---

## 🔄 Booking & Payment Flow

For a detailed walkthrough of how a booking request moves through the system — lock acquisition, seat reservation, payment processing, lease renewal, and idempotency — see **[BOOKING_AND_PAYMENT_FLOW.md](BOOKING_AND_PAYMENT_FLOW.md)**.

---

## 🎯 Outcome

* Exactly-once booking guarantee per seat
* No duplicate entries in database
* Safe retry handling

---

## ⚡ TL;DR

> This project showcases how to build a **race-condition-safe ticket booking system** using Redis distributed locks and idempotency — a common pattern in real-world payment and booking systems.

---
