package edu.cornell.gdiac.nightbite.ball;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import edu.cornell.gdiac.nightbite.HoleModel;
import edu.cornell.gdiac.nightbite.InputController;
import edu.cornell.gdiac.nightbite.WorldController;
import edu.cornell.gdiac.nightbite.obstacle.CapsuleObstacle;
import edu.cornell.gdiac.nightbite.obstacle.Obstacle;
import edu.cornell.gdiac.nightbite.obstacle.BoxObstacle;
import edu.cornell.gdiac.nightbite.obstacle.PolygonObstacle;

public class BallController extends WorldController implements ContactListener {

    /** Reference to the ball texture */
    private static final String BALL_TEXTURE = "ball/ballSprite.png";
    /** Texture assets for the ball */
    private TextureRegion ballTexture;
    private TextureRegion itemTexture;

    /** The initial ball position */
    private static Vector2 BALL_POS = new Vector2(24, 4);
    /** Reference to the ball/player avatar */
    private BallModel ball;

    private BoxObstacle item;
    private boolean itemActive = true;

    /** Density of objects */
    private static final float BASIC_DENSITY   = 0.0f;
    /** Friction of objects */
    private static final float BASIC_FRICTION  = 1f;
    /** Collision restitution for all objects */
    private static final float BASIC_RESTITUTION = 0f;

    private static final float DAMPING = 0.3f;

    private static final float[] WALL3 = { 4.0f, 10.5f,  8.0f, 10.5f,
            8.0f,  9.5f,  4.0f,  9.5f};

    public void preLoadContent(AssetManager manager) {
        manager.load(BALL_TEXTURE, Texture.class);
        assets.add(BALL_TEXTURE);
        manager.load("item/item.png", Texture.class);
        assets.add("item/item.png");
        super.preLoadContent(manager);
    }

    public void loadContent(AssetManager manager) {
        ballTexture = createTexture(manager,BALL_TEXTURE,false);
        super.loadContent(manager);
        itemTexture = createTexture(manager, "item/item.png", false);
    }

    public BallController() {
        setDebug(false);
        setComplete(false);
        setFailure(false);
        world.setContactListener(this);
        world.setGravity(new Vector2(0, 0));
    }

    public void reset() {
        Vector2 gravity = new Vector2( world.getGravity() );

        for(Obstacle obj : objects) {
            obj.deactivatePhysics(world);
        }
        objects.clear();
        addQueue.clear();
        world.dispose();

        world = new World(gravity,false);
        world.setContactListener(this);
        setComplete(false);
        setFailure(false);
        populateLevel();
    }

    private void populateLevel() {

        // add an obstacle
        PolygonObstacle obj;
        obj = new PolygonObstacle(WALL3, 0, 0);
        obj.setBodyType(BodyDef.BodyType.StaticBody);
        obj.setDensity(BASIC_DENSITY);
        obj.setFriction(BASIC_FRICTION);
        obj.setRestitution(BASIC_RESTITUTION);
        obj.setDrawScale(scale);
        obj.setTexture(earthTile);
        obj.setName("wall1");
        addObject(obj);

        // add an obstacle
        obj = new PolygonObstacle(WALL3, 10, -5);
        obj.setBodyType(BodyDef.BodyType.StaticBody);
        obj.setDensity(BASIC_DENSITY);
        obj.setFriction(BASIC_FRICTION);
        obj.setRestitution(BASIC_RESTITUTION);
        obj.setDrawScale(scale);
        obj.setTexture(earthTile);
        obj.setName("wall2");
        addObject(obj);

        // add an obstacle
        obj = new HoleModel(WALL3, 20, 0);
        obj.setBodyType(BodyDef.BodyType.StaticBody);
        obj.setDensity(BASIC_DENSITY);
        obj.setFriction(BASIC_FRICTION);
        obj.setRestitution(BASIC_RESTITUTION);
        obj.setDrawScale(scale);
        obj.setTexture(earthTile);
        addObject(obj);

        // add item
        float ddwidth  = itemTexture.getRegionWidth()/scale.x;
        float ddheight = itemTexture.getRegionHeight()/scale.y;
        item = new BoxObstacle(2, 4, ddwidth, ddheight);
        item.setDrawScale(scale);
        item.setTexture(itemTexture);
        item.setName("item");
        addObject(item);

        // add ball
        float dwidth  = ballTexture.getRegionWidth()/scale.x;
        float dheight = ballTexture.getRegionHeight()/scale.y;
        ball = new BallModel(BALL_POS.x, BALL_POS.y, dwidth, dheight);
        ball.setDrawScale(scale);
        ball.setTexture(ballTexture);
        addObject(ball);
    }

    public void update(float dt) {
        if (! itemActive) { removeItem(); }
        ball.setIX(InputController.getInstance().getHorizontal());
        ball.setIY(InputController.getInstance().getVertical());
        if (InputController.getInstance().didBoost()) {
            ball.setBoostImpulse(InputController.getInstance().getHorizontal(), InputController.getInstance().getVertical());
        }
        ball.applyImpulse();

        if (!ball.isAlive()) {
            ball.respawn();
        }
    }

    public void beginContact(Contact contact) {
        Object a = contact.getFixtureA().getBody().getUserData();
        Object b = contact.getFixtureB().getBody().getUserData();

        if (a instanceof HoleModel) {
            if (b instanceof BallModel) {
                ((BallModel) b).setAlive(false);
                ((BallModel) b).draw = false;
            }
            return;
        }

        if (a instanceof BoxObstacle && ((BoxObstacle) a).getName().equals("item")) {
            if (b instanceof BallModel) {
                ((BallModel) b).item = true;
                itemActive = false;
            }
        }




    }

    public void endContact(Contact contact) {
    }

    public void postSolve(Contact contact, ContactImpulse impulse) {
    }

    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    private void removeItem() {
        item.draw = false;
        item.setActive(false);
    }

    private void addItem(Vector2 position) {
        item.draw = false;
        item.setActive(false);
        item.setPosition(position);
    }


}
