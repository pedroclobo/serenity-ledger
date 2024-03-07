# Goals
- Blockchain system with high dependability guarantees
- Use Istanbul BFT consensus algorithm
- Extend incomplete Istanbul BFT consensus algorithm
    - Implement round change protocol to select new leader
    - Doesn't handle faulty leaders
    - ...

    - Static view, all processes know each other
    - Public key infrastructure: keys are previously distributed

# Design Requirements
- Consists of two parts:
    1. Client library that is linked with the application that users employ to access the system
    2. Server-side logic responsible for keeping the state of the system and collectively implement the blockchain service

    - Library is client that translates application calls into requests to the service (triggers instances of the consensus)
    
    - Client messages that do not meet the expected behavior must be detected and handled appropriately

    - Servers run the consensus algorithm and maintain the state of the system

## Assumptions
- Up to 1/3 of the servers can fail
- The network is unreliable: channels can drop, duplicate or corrupt messages. Channels are not secure.
    - Basic implementation is done using UDP, which approximates fair-loss links

## First Stage
- Use of simplified client that submits requests to the service: <append, string>, which appends a string to the blockchain. It returns success and indicates where the string was appended.

- Tests that check the system's correctness and change the way message are delivered or the behavior of Byzantine nodes

# Implementation Requirements
1. Correctness of the protocol implementation
2. Quality of the code and report
3. Performance of the implemented protocol
4. Quality of the test suite

# Istanbul BFT Consensus Algorithm
## Abstract
- Byzantine fault-tolerant consensus algorithm
- State machine replication in Quorum blockchain (open-source blockchain platform) (used in Ethereum)
- Assumes partially synchronous model
    - Safety does not depend on timing assumptions
    - Liveness depends on periods of synchrony

- Leader-based algorithm
- Tolerates f failures where n >= 3f + 1
- Quadratic message complexity
- Three message delays

## System Model
- Global stabilization time: time after which the network becomes (partially) synchronous
    - Before the GST, any message can be arbitrarily delayed or lost
    - After the GST, messages sent by a correct process are guaranteed to be delivered by every correct process within a known time bound

## Algorithm
- Lambda represents algorithm instance on process p

- Agreement: If a correct process decides some value v, then no correct process decides a value v' such that v' != v.
- Validity: Given an externally provided predicate b, if a correct process decides some value v, then b(v) is true. b dictates if the value is valid in the context of the application.
- Termination: Every correct process eventually decides.

- Algorithm 1:
    - lambda represents the consensus instance, can be used to identify the block number
    - ri is the round number and starts at 1
    - timer is a timer that is used to trigger the timeout event, triggering a round change. the timer is exponential in the round number

    - leader broadcasts pre-prepare message with input value

- there is a function LEADER(lambda, r) that identifies the leader

- ⟨ROUND-CHANGE, lambda, r, pr, pv⟩: used to ensure progress when the current leader is suspected to be faulty or communication is unreliable
    - lambda: consensus instance
    - r: round number
    - pr: prepared round
    - pv: prepared value

- each upon rule is triggered only once for any round
    - only exception is algorithm 3, line 17

- processes only accept message if they are considered to be valid
- prepared round needs to be smaller than the current round

- round changes ensure liveness, when communication is unreliable or the leader is faulty

- upon rule at line 5 is not strictly necessary, just improves performance

- If some correct process could have decided a value v at some
round r < r', then v must be the value proposed in a PRE-PREPARE
message for round r'.