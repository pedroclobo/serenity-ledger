package pt.ulisboa.tecnico.hdsledger.service.fake_leader;

import pt.ulisboa.tecnico.hdsledger.service.ByzantineBehaviorTest;

class FakeLeaderByzantineBehaviorTest extends ByzantineBehaviorTest {

  public FakeLeaderByzantineBehaviorTest() {
    super("fake_leader/fake_leader.json", "client_config.json");
  }

}
