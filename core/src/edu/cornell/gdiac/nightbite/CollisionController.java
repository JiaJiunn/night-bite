package edu.cornell.gdiac.nightbite;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import edu.cornell.gdiac.nightbite.entity.*;
import edu.cornell.gdiac.nightbite.obstacle.BoxObstacle;
import edu.cornell.gdiac.nightbite.obstacle.CapsuleObstacle;


public class CollisionController implements ContactListener {
    /** Impulse for players pushing */
    protected static final float PUSH_IMPULSE = 200f;

    /** Impulse for player knockback from detonating firecracker*/
    protected static final float KNOCKBACK_IMPULSE = 150f;

    public static final int ITEMS_TO_WIN = 3;

    private WorldModel worldModel;

    public CollisionController(WorldModel worldModel) {
        this.worldModel = worldModel;
    }

    /**
     * Called when two fixtures begin to touch
     */
    public void beginContact(Contact contact) {
        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();

        // Player-Object Contact
        if (a instanceof PlayerModel) {
            handlePlayerToObjectContact((PlayerModel) a, b);
        } else if (b instanceof PlayerModel) {
            handlePlayerToObjectContact((PlayerModel) b, a);
        }

        // Item-Object Contact
        if (a instanceof ItemModel) {
            handleItemToObjectContact((ItemModel) a, b);
        } else if (b instanceof ItemModel) {
            handleItemToObjectContact((ItemModel) b, a);
        }
    }

    /**
     * Called when two fixtures cease to touch
     */
    public void endContact(Contact contact) {
        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();

        if (a instanceof PlayerModel && b instanceof BoxObstacle && ((BoxObstacle) b).getName().equals("item")) {
            int itemId = ((ItemModel) b).getId();
            ((PlayerModel) a).setOverlapItem(itemId, false);
        } else if (b instanceof PlayerModel && a instanceof BoxObstacle && ((BoxObstacle) a).getName().equals("item")) {
            int itemId = ((ItemModel) a).getId();
            ((PlayerModel) b).setOverlapItem(itemId, false);
        }
    }

    /**
     * Called after a contact is updated
     */
    public void preSolve(Contact contact, Manifold oldManifold) {
        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();

        // Firecracker-Object Contact
        if (a instanceof FirecrackerModel) {
            handleFirecrackerToObjectContact((FirecrackerModel) a, b);
        } else if (b instanceof FirecrackerModel) {
            handleFirecrackerToObjectContact((FirecrackerModel) b, a);
        }

        // Players walk through items and firecrackers
        if ((a instanceof PlayerModel && b instanceof ItemModel) || (b instanceof PlayerModel && a instanceof ItemModel)) {
            contact.setEnabled(false);
        } else if ((a instanceof ItemModel && ((ItemModel) a).holdingPlayer != null) || (b instanceof ItemModel && ((ItemModel) b).holdingPlayer != null)) {
            contact.setEnabled(false);
        } else if ((a instanceof PlayerModel && b instanceof FirecrackerModel) || (b instanceof PlayerModel && a instanceof FirecrackerModel)) {
            contact.setEnabled(false);
        }

    }

    /**
     * Inspect a contact after solver is finished
     */
    public void postSolve(Contact contact, ContactImpulse impulse) {
        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();

        // Player-Player Contact
        if (a instanceof PlayerModel && b instanceof PlayerModel) {
            PlayerModel playerA = (PlayerModel) a;
            PlayerModel playerB = (PlayerModel) b;

            Vector2 flyDirection;
            if (playerA.state == PlayerModel.MoveState.RUN && playerB.state != PlayerModel.MoveState.RUN) {
                flyDirection = playerB.getLinearVelocity().nor();
                playerA.resetBoosting();
                playerB.getBody().applyLinearImpulse(flyDirection.scl(PUSH_IMPULSE), playerB.getPosition(), true);
            } else if (playerB.state == PlayerModel.MoveState.RUN && playerA.state != PlayerModel.MoveState.RUN) {
                flyDirection = playerA.getLinearVelocity().nor();
                playerA.getBody().applyLinearImpulse(flyDirection.scl(PUSH_IMPULSE), playerA.getPosition(), true);
                playerB.resetBoosting();
            } else if (playerB.state == PlayerModel.MoveState.RUN && playerA.state == PlayerModel.MoveState.RUN) {
                flyDirection = playerA.getLinearVelocity().nor();
                playerA.getBody().applyLinearImpulse(flyDirection.scl(PUSH_IMPULSE), playerA.getPosition(), true);
                flyDirection = playerB.getLinearVelocity().nor();
                playerA.resetBoosting();
                playerB.resetBoosting();
                playerB.getBody().applyLinearImpulse(flyDirection.scl(PUSH_IMPULSE), playerB.getPosition(), true);
            }
        }

    }

    public void handlePlayerToObjectContact(PlayerModel player, Object object) {
        if (object instanceof HoleModel) {

            // Player-Hole collision
            player.setDead();

            if (player.hasItem()) { // TODO fix jank implementation
                for (ItemModel item_obj : player.getItems()) {
                    item_obj.startRespawn();
                }
                player.clearInventory();
            }

        } else if (object instanceof ItemModel) {

            // Player-Item
            int id = ((ItemModel) object).getId();
            player.setOverlapItem(id, true);

        } else if (object instanceof HomeModel) {

            // Player-Home
            HomeModel homeObject = (HomeModel) object;
            // If players went to their own home, drop off item and increment score
            if (player.getTeam().equals(homeObject.getTeam()) && player.hasItem()) {

                homeObject.incrementScore(1);

                for (ItemModel item_obj : player.getItems()) {
                    item_obj.startRespawn();
                }
                player.clearInventory();

                // win condition
                checkWinCondition(homeObject);
            }

        } else if (object instanceof FirecrackerModel) {

            // Player-Firecracker
            FirecrackerModel firecracker = (FirecrackerModel) object;

            // If the firecracker is detonating, player should be knocked back
            // TODO and stunned temporarily
            if (firecracker.isDetonating()) {
                Vector2 blastDirection = player.getPosition().sub(firecracker.getPosition()).nor();
                blastDirection = new Vector2(blastDirection.x, blastDirection.y);  // jank but will break without
                blastDirection.scl(KNOCKBACK_IMPULSE);
                player.getBody().applyLinearImpulse(blastDirection, player.getPosition(), true);
                // Gdx.app.log("Firecracker Collision after applying impulse", blastDirection.toString());
            }
        }
    }

    public void handleItemToObjectContact(ItemModel item, Object object) {
        if (object instanceof HoleModel) {
            PlayerModel p = item.holdingPlayer;
            if (p == null) {
                item.startRespawn();
            }
        } else if (object instanceof HomeModel && item.lastTouch.getTeam().equals(((HomeModel) object).getTeam())) {
            PlayerModel p = item.holdingPlayer;
            if (p == null) {
                item.startRespawn();
                // add score
                HomeModel homeObject = (HomeModel) object;
                homeObject.incrementScore(1);

                // check win condition
                checkWinCondition(homeObject);
            }
        }
    }

    /**
     * Collision handler for firecrackers to objects
     */
    public void handleFirecrackerToObjectContact(FirecrackerModel firecracker, Object object) {
        if (object instanceof HoleModel) {
            firecracker.markRemoved(true);
        }
    }

    public void checkWinCondition(HomeModel homeObject) {
        if (homeObject.getScore() >= ITEMS_TO_WIN) {
            worldModel.completeLevel();
            if (homeObject.getTeam().equals("teamA")) {
                worldModel.winner = "PLAYER 1 ";
            } else if (homeObject.getTeam().equals("teamB")) {
                worldModel.winner = "PLAYER 2 ";
            }
        }
    }
}
