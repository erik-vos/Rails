package net.sf.rails.game.state;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.*;

import net.sf.rails.game.state.BooleanState;
import net.sf.rails.game.state.Root;

import org.junit.Before;
import org.junit.Test;

public class BooleanStateTest {

    private final static String DEFAULT_ID = "Default";
    private final static String INIT_ID = "Init"; 

    private Root root;
    
    private BooleanState stateDefault;
    private BooleanState stateInit;

    @Before
    public void setUp() {
        root = StateTestUtils.setUpRoot();
        stateDefault = new BooleanState(root,  DEFAULT_ID);
        stateInit = new BooleanState(root, INIT_ID, true);
        
    }

    @Test
    public void testValue() {
        assertFalse(stateDefault.value());
        assertTrue(stateInit.value());
    }
    
    @Test
    public void testSet() {
        stateDefault.set(true);
        assertTrue(stateDefault.value());
        stateInit.set(false);
        assertFalse(stateInit.value());
    }

    @Test
    public void testSetSameIgnored() {
        stateDefault.set(false);
        stateInit.set(true);
        StateTestUtils.close(root);
        assertThat(StateTestUtils.getPreviousChangeSet(root).getStates()).doesNotContain(stateDefault, stateInit);
    }
    
    @Test
    public void testUndoRedo() {
        assertFalse(stateDefault.value());
        stateDefault.set(true);
        assertTrue(stateDefault.value());
        StateTestUtils.close(root);
        assertThat(StateTestUtils.getPreviousChangeSet(root).getStates()).contains(stateDefault);
        StateTestUtils.undo(root);
        assertFalse(stateDefault.value());
        StateTestUtils.redo(root);
        assertTrue(stateDefault.value());
    }

}
