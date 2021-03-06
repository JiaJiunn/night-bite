package edu.cornell.gdiac.nightbite;

import box2dLight.RayHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import edu.cornell.gdiac.nightbite.entity.*;
import edu.cornell.gdiac.nightbite.obstacle.Obstacle;
import edu.cornell.gdiac.util.ExitCodes;
import edu.cornell.gdiac.util.LightSource;
import edu.cornell.gdiac.util.PointSource;
import edu.cornell.gdiac.util.PooledList;

import java.util.*;

public class WorldModel {
    /** World width in Box2D units */
    public static final float WORLD_WIDTH = 20f;
    /** World height in Box2D units */
    public static final float WORLD_HEIGHT = 12f;
    /** Canonical view width in pixels */
    public static final float CANONICAL_WIDTH = 1280f;
    /** Canonical view height in pixels */
    public static final float CANONICAL_HEIGHT = 768f;
    /** How many frames after winning/losing do we continue? */
    public static final int EXIT_COUNT = 150;
    /** Padding for actual view in pixels */
    private static final float PADDING = 0f;

    /** The winner of the level. */
    public String winner;

    /** Reference to the player */
    private PlayerModel player;

    /** The Box2D world for physics objects */
    protected World world;
    /** The camera defining the RayHandler view; scale is in physics coordinates */
    protected OrthographicCamera raycamera;
    /** The rayhandler for storing lights, and drawing them */
    protected RayHandler rayhandler;
    /** World scale */
    protected Vector2 scale;
    /** Scale of actual displayed window */
    protected Vector2 actualScale;
    /** World bounds */
    private Rectangle bounds;
    /** Whether we have completed this level */
    private boolean complete;
    /** Countdown active for winning or losing */
    private int countdown;

    /** List of players */
    private ArrayList<PlayerModel> players;

    private PooledList<HumanoidModel> enemies;
    private PooledList<CrowdModel> crowds;

    /** List of items */
    private ArrayList<ItemModel> items;
    /** Whether the player is overlapping the items */
    private ArrayList<Boolean> overlapItem = new ArrayList<>();
    /** List of firecrackers */
    private PooledList<FirecrackerModel> firecrackers;
    private PooledList<FirecrackerModel> crowdUnits;
    /** List of oils */
    private HashMap<Integer, OilModel> oils;
    private ArrayList<OilModel> removedOils = new ArrayList<>();
    private int oilIndCounter = 0;
    private static final int MAX_OIL = 5;
    /** Objects that don't move during updates */
    private PooledList<Obstacle> staticObjects;
    /** All of the lights that we loaded from the JSON file */
    private Array<LightSource> lights = new Array<>();
    /** Bottom layer background textures */
    private TextureRegion[][] background = new TextureRegion[20][12];
    /** 1st layer foreground textures */
    private Sprite[][] brick = new Sprite[20][12];
    /** 2nd layer foreground textures */
    private Sprite[][] lantern = new Sprite[20][12];

    private AILattice aiLattice;
    public int LEVEL_COMPLETED = 0;
    public int LEVEL_TIME_OUT = 1;
    // Level exit codes
    private int LEVEL_EXIT_CODE;

    // TODO: REMOVE
    public Debug debug;

    public WorldModel() {
        world = new World(Vector2.Zero, false);
        bounds = new Rectangle(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        scale = new Vector2(1f, 1f);
        actualScale = new Vector2(1f, 1f);
        complete = false;
        countdown = -1;
        players = new ArrayList<>();
        items = new ArrayList<>();
        firecrackers = new PooledList<>();
        staticObjects = new PooledList<>();
        enemies = new PooledList<>();
        crowds = new PooledList<>();
        oils = new HashMap<>();

        // TODO: REMOVE
        debug = new Debug();
    }

    /**
     * Returns a string equivalent to the COMPLEMENT of bits in s
     * <p>
     * This function assumes that s is a string of 0s and 1s of length < 16.
     * This function allows the JSON file to specify exclusion bit arrays (for masking)
     * in a readable format.
     *
     * @param s the string representation of the bit array
     * @return a string equivalent to the COMPLEMENT of bits in s
     */
    public static short bitStringToComplement(String s) {
        short value = 0;
        short pos = 1;
        for (int ii = s.length() - 1; ii >= 0; ii--) {
            if (s.charAt(ii) == '0') {
                value += pos;
            }
            pos *= 2;
        }
        return value;
    }

    public World getWorld() {
        return world;
    }

    public PlayerModel getPlayer() {
        return player;
    }

    public void setPlayer(PlayerModel player) {
        this.player = player;
    }

    /**
     * Returns true if the level is completed.
     * <p>
     * If true, the level will advance after a countdown
     *
     * @return true if the level is completed.
     */
    public boolean isComplete() {
        return complete;
    }

    public Iterable<ItemModel> getItemIter() {
        class ItemIterable implements Iterable<ItemModel> {
            @Override
            public Iterator<ItemModel> iterator() {
                return items.iterator();
            }
        }
        return new ItemIterable();
    }

    public ItemModel getItem(int index) {
        return items.get(index);
    }

    public int getNumItems() {
        return items.size();
    }

    /**
     * Complete the level
     */
    public void completeLevel(boolean passedLevel) {
        if (!complete) {
            countdown = EXIT_COUNT;
        }
        complete = true;
        if (passedLevel) {
            LEVEL_EXIT_CODE = ExitCodes.LEVEL_PASS;
        } else {
            LEVEL_EXIT_CODE = ExitCodes.LEVEL_FAIL;
        }
    }

    public int getLevelExitCode() {
        return LEVEL_EXIT_CODE;
    }

    public void setContactListener(ContactListener c) {
        world.setContactListener(c);
    }

    /**
     * Basically collapse all of those data structures into one giant iterable.
     *
     * @return Combined iterable of all obstacles in the world
     */
    public Iterable<Obstacle> getObjects() {
        class comp implements Comparator<Obstacle> {
            @Override
            public int compare(Obstacle obstacle, Obstacle t1) {
                return (int) Math.signum((- obstacle.getBottom() + t1.getBottom()));
            }
        }

        // Overkill, but I'm bored. Also this will probably help like a lot.
        class objectIterable implements Iterator<Obstacle> {
            // Raw iterator. I love unsafe code.
            // Make sure each of the iterators inside iters extend Obstacle.
            // Please.
            final List<?>[] objs = {
                    staticObjects,
                    new ArrayList<OilModel>(oils.values()),
                    removedOils,
                    items,
                    players,
                    enemies,
                    firecrackers,
            };

            final Obstacle[] peek = new Obstacle[objs.length];

            final Iterator<?>[] iters = new Iterator<?>[objs.length];

            final Comparator<Obstacle> comp = new comp();

            public objectIterable() {
                for (List o : objs) {
                    o.sort(comp);
                }
                for (int i = 0; i < objs.length; i ++) {
                    iters[i] = objs[i].iterator();
                }
                for (int i = 0; i < objs.length; i ++) {
                    try{
                        peek[i] = (Obstacle) iters[i].next();
                    } catch (NoSuchElementException e) {
                        peek[i] = null;
                    }
                }
            }

            // TODO: Do i want to make this more efficient?
            @Override
            public boolean hasNext() {
                for (Obstacle o : peek) {
                    if (o != null) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Obstacle next() {
                Obstacle min = null;
                int m = -1;
                for (int i = 0; i < iters.length; i ++) {
                    if (min == null) {
                        min = peek[i];
                        m = i;
                        continue;
                    }

                    if (peek[i] == null) {
                        continue;
                    }

                    if (peek[i].getBottom() > min.getBottom()) {
                        min = peek[i];
                        m = i;
                        continue;
                    }
                }

                if (min == null) {
                    throw new NoSuchElementException();
                } try {
                    peek[m] = (Obstacle) iters[m].next();
                } catch (NoSuchElementException e) {
                    peek[m] = null;
                }
                return min;

                // for (Iterator<?> iter : iters) {
                //     if (iter.hasNext()) {
                //         return (Obstacle) iter.next();
                //     }
                // }
                // throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }

        class objectIterator implements Iterable<Obstacle> {
            @Override
            public Iterator<Obstacle> iterator() {
                return new objectIterable();
            }
        }

        return new objectIterator();
    }

    public void setScale(float sx, float sy) {
        scale.set(sx, sy);
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public float getHeight() {
        return bounds.height;
    }

    public float getWidth() {
        return bounds.width;
    }


    public boolean isDone() {
        countdown--;
        return countdown <= 0 && complete;
    }

    /**
     * Returns the world's rayhandler.
     */
    public RayHandler getRayhandler() {
        return rayhandler;
    }

    /**
     * Returns the world's lights.
     */
    public Array<LightSource> getLights() {
        return lights;
    }

    // TODO: player model refactor
    public ArrayList<PlayerModel> getPlayers() {
        return players;
    }

    public PooledList<HumanoidModel> getEnemies() { return enemies; }

    public PooledList<CrowdModel> getCrowds() { return crowds; }

    public Vector2 getScale() {
        return scale;
    }

    public void setScale(Vector2 scale) {
        setScale(scale.x, scale.y);
    }

    public Vector2 getActualScale() {
        return actualScale;
    }

    public void worldStep(float step, int vel, int posit) {
        world.step(step, vel, posit);
    }

    /**
     * Returns true if the object is in bounds.
     * <p>
     * This assertion is useful for debugging the physics.
     *
     * @param obj The object to check.
     * @return true if the object is in bounds.
     */
    public boolean inBounds(Obstacle obj) {
        boolean horiz = (bounds.x <= obj.getX() && obj.getX() <= bounds.x + bounds.width);
        boolean vert = (bounds.y <= obj.getY() && obj.getY() <= bounds.y + bounds.height);
        return horiz && vert;
    }

    /**
     * Transform an object from tile coordinates to canonical world coordinates.
     * <p>
     * Tile coordinates dictate that the bottom left tile is (0, 0), but in order to place an object in world
     * coordinates, it must be centered on (0.5, 0.5).
     *
     * @param obj The obstacle to be transformed
     */
    public void transformTileToWorld(Obstacle obj) {
        Vector2 pos = transformTileToWorld(obj.getPosition());
        obj.setPosition(pos);
    }

    /**
     * Transform an vector from tile coordinates to canonical world coordinates.
     *
     * @param pos Position of the tile
     * @return New position of the tile using the transformation
     */
    public Vector2 transformTileToWorld(Vector2 pos) {
        Affine2 transformation = new Affine2();
        transformation.setToTranslation(0.5f, 0.5f);
        transformation.applyTo(pos);
        return pos;
    }

    private void initializeObject(Obstacle obj) {
        assert inBounds(obj);
        transformTileToWorld(obj);
        obj.activatePhysics(world);
    }

    public void addPlayer(PlayerModel player) {
        initializeObject(player);
        players.add(player);
    }

    public void addStaticObject(ImmovableModel obj) {
        initializeObject(obj);
        staticObjects.add(obj);
    }

    public void addItem(ItemModel item) {
        initializeObject(item);
        items.add(item);
        overlapItem.add(false);
    }

    public void addEnemy(HumanoidModel enemy) {
        initializeObject(enemy);
        enemies.add(enemy);
    }

    public void addCrowd(CrowdModel crowd) {
        for (CrowdUnitModel crowdUnit: crowd.getCrowdUnitList()) {
            initializeObject(crowdUnit);
            enemies.add(crowdUnit);
        }
        crowds.add(crowd);
    }

    public void initializeAI() {
//        System.out.println(bounds);
        aiLattice = new AILattice((int) bounds.width, (int) bounds.height);
        aiLattice.populateStatic(staticObjects);
    }

    public AILattice getAILattice() {
        return aiLattice;
    }

    public void debugAI(GameCanvas canvas) {
        aiLattice.drawDebug(canvas, scale);
    }


    // I made this return FirecrackerModel so you can get the thing you just thrown
    /**
     * Creates a firecracker at the specified position, usually the position of the enemy (in tiles)
     * TODO currently called in levelcontroller... may want to change
     *
     * @param x The x position of the firecracker enemy
     * @param y The y position of the firecracker enemy
     */
    public FirecrackerModel addFirecracker(float x, float y) {
        FirecrackerModel firecracker = new FirecrackerModel(world, x, y, 1, 1);
        firecracker.setDrawScale(getScale());
        firecracker.setActualScale(getActualScale());
        initializeObject(firecracker);
        firecrackers.add(firecracker);
        return firecracker;
    }

    public OilModel addOil(float x, float y) {
        OilModel oil = new OilModel(x, y);
        oil.setDrawScale(getScale());
        oil.setActualScale(getActualScale());
        oil.activatePhysics(world);
        if (oils.size() >= MAX_OIL) { // If there are already 5 oils dropped, overwrite oldest one
            OilModel oldOil = oils.get(oilIndCounter);
            oldOil.deactivatePhysics(world);
        }
        oils.put(oilIndCounter, oil);
        oilIndCounter = (oilIndCounter + 1) % MAX_OIL;
        return oil;
    }

    public boolean canAddOil() {
        return oils.size() < MAX_OIL;
    }

    public void removeOil(OilModel oil) { // Reorder existing oils to ensure FIFO removal
        int removedInd = -1;
        for (Map.Entry<Integer, OilModel> entry : oils.entrySet()) {
            if (Objects.equals(oil, entry.getValue())) {
                removedInd = entry.getKey();
            }
        }

        for (int i = removedInd < oilIndCounter ? removedInd+MAX_OIL : removedInd; i > oilIndCounter; i--) {
            int ind1 = i % MAX_OIL;
            int ind2 = (i-1) % MAX_OIL;
            if (oils.get(ind2) != null) {
                oils.put(ind1, oils.get(ind2));
            } else {
                oils.remove(ind1);
            }
        }
        removedOils.add(oil);
        oil.markRemoved(true);
        oils.remove(oilIndCounter % MAX_OIL);
    }

    public PooledList<FirecrackerModel> getFirecrackers() {
        return firecrackers;
    }

    /**
     * TODO allow passing in of different lighting parameters
     */
    public void initLighting(GameCanvas canvas) {
        // TODO; make all this work with non diagonal scaling
        raycamera = new OrthographicCamera(canvas.getWidth() / scale.x, canvas.getHeight() / scale.y);
        raycamera.position.set(canvas.getWidth() / scale.x / 2, canvas.getHeight() / scale.y / 2, 0);
        raycamera.update();

        RayHandler.setGammaCorrection(true);
        RayHandler.useDiffuseLight(true);
        rayhandler = new RayHandler(world, canvas.getWidth(), canvas.getWidth());
        rayhandler.setCombinedMatrix(raycamera);

        // See https://www.informit.com/articles/article.aspx?p=1616796&seqNum=5
        rayhandler.diffuseBlendFunc.set(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        // All hard coded for now, to be changed with data-driven levels
        float[] color = new float[]{0.01f, 0.005f, 0.0f, 1.0f};
        rayhandler.setAmbientLight(color[0], color[1], color[2], color[3]);
        int blur = 2;
        rayhandler.setBlur(blur > 0);
        rayhandler.setBlurNum(blur);
    }

    /**
     * Creates one point light, which goes in all directions.
     *
     * @param color The rgba value of the light color.
     * @param dist  The radius of the light.
     */
    public PointSource createPointLight(float[] color, float dist) {
        // ALL HARDCODED!
        float[] pos = new float[]{0.0f, 0.0f};
        int rays = 512;

        PointSource point = new PointSource(rayhandler, rays, Color.WHITE, dist, pos[0], pos[1]);
        point.setColor(color[0], color[1], color[2], color[3]);
        point.setSoft(true);

        // Create a filter to exclude see through items
        Filter f = new Filter();
        f.maskBits = bitStringToComplement("1111"); // controls collision/cast shadows
        point.setContactFilter(f);
        point.setActive(true);
        lights.add(point);
        return point;
    }

    /**
     * Make a point light that is static and unmoving.
     *
     * @param color
     * @param dist
     * @param x
     * @param y
     * @return
     */
    public PointSource createStaticPointLight(float[] color, float dist, float x, float y) {
        // ALL HARDCODED!
        PointSource point = new PointSource(rayhandler, 512, Color.WHITE, dist, x + 0.5f, y + 0.5f);
        point.setColor(color[0], color[1], color[2], color[3]);
        point.setSoft(true);

        // Create a filter to exclude see through items
        Filter f = new Filter();
        f.maskBits = bitStringToComplement("1111"); // controls collision/cast shadows
        point.setContactFilter(f);
        point.setActive(true);
        point.setStaticLight(true);
        lights.add(point);
        return point;
    }

    public void updateAndCullObjects(float dt) {
        // TODO: Do we need to cull staticObjects?
        // TODO: This is also unsafe
        Iterator<?>[] cullAndUpdate = {staticObjects.entryIterator(), firecrackers.entryIterator()};
        Iterator<?>[] updateOnly = {players.iterator(), items.iterator()};

        for (Iterator<?> iterator : cullAndUpdate) {
            while (iterator.hasNext()) {
                PooledList<?>.Entry entry = (PooledList<?>.Entry) iterator.next();
                Obstacle obj = (Obstacle) entry.getValue();
                if (obj.isRemoved()) {
                    obj.deactivatePhysics(world);
                    entry.remove();
                } else {
                    // Note that update is called last!
                    obj.update(dt);
                }
            }
        }

        for (OilModel oil : oils.values()) { // Update spilled oils
            oil.update(dt);
        }
        ArrayList<OilModel> doneDissolving = new ArrayList<>();
        for (OilModel oil : removedOils) { // Deactivate removed oils
            oil.deactivatePhysics(world);
            oil.update(dt);
            if (oil.isDissolved()) {
                doneDissolving.add(oil);
            }
        }
        for(OilModel oil : doneDissolving) {
            removedOils.remove(oil);
        }

        for (Iterator<?> iterator : updateOnly) {
            while (iterator.hasNext()) {
                ((Obstacle) iterator.next()).update(dt);
            }
        }

        aiLattice.clearDynamic();
        aiLattice.populateDynamic(downcastIterable(players));
        aiLattice.populateDynamic(downcastIterable(enemies));
        // aiLattice.populateDynamic(downcastIterable(enemies));

        // TODO: REMOVE
        debug.updatePathfinding(aiLattice);

    }

    private Iterable<Obstacle> downcastIterable(Iterable<?> iter) {
        // AGAIN, unsafe
        class ObsIterator implements Iterator<Obstacle> {
            Iterator<?> iterator;

            public ObsIterator(Iterator<?> iter) {
                iterator = iter;
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Obstacle next() {
                return (Obstacle) iterator.next();
            }
        }

        class ObsIterable implements Iterable<Obstacle> {
            @Override
            public Iterator<Obstacle> iterator() {
                return new ObsIterator(iter.iterator());
            }
        }

        return new ObsIterable();
    }

    public void dispose() {
        for (Obstacle obj : getObjects()) {
            obj.deactivatePhysics(world);
        }

        for (LightSource light : lights) {
            light.remove();
        }
        lights.clear();

        if (rayhandler != null) {
            rayhandler.dispose();
            rayhandler = null;
        }

        // TODO: Clear other stuff
        staticObjects.clear();
        // Honestly this is kind of dumb.
        // We can literally just dereference the WorldModel and all this should go away.
        staticObjects = null;
        world = null;
        scale = null;
    }

    public void setBackground(TextureRegion textureRegion, int x, int y) {
        background[x][y] = textureRegion;
    }

    public void setBrick(Sprite sprite, int x, int y) {
        brick[x][y] = sprite;
    }

    public void setLantern(Sprite sprite, int x, int y) {
        lantern[x][y] = sprite;
    }

    public void setHoleEdge(Sprite sprite, int x, int y) {
        // Kinda jank because it assumes that the tail object from staticObjects
        // is the hole object for the corresponding tile coordinate
        try {
            ((HoleModel) staticObjects.getTail()).addHoleEdge(sprite);
        } catch (Exception e) {
            return;
        }
    }

    public void setPixelBounds() {
        // TODO: Optimizations; only perform this calculation if the canvas size has changed or something

        // The whole point is that if the canvas is DEFAULT_PIXEL_WIDTH x DEFAULT_PIXEL_HEIGHT and
        // the world is DEFAULT_WIDTH x DEFAULT_HEIGHT, everything is unscaled.
        // These are called the canonical pixel space and canonical world space respectively.

        // World2Pixel translates from canonical world space to canonical pixel space
        // Assumes the ratio from DEFAULT_HEIGHT and DEFAULT_PIXEL_HEIGHT is the same as the ratio from
        // DEFAULT_WIDTH and DEFAULT_PIXEL_WIDTH
        float scaleWorldToCanonical = Math.min(CANONICAL_WIDTH / WORLD_WIDTH, CANONICAL_HEIGHT / WORLD_HEIGHT);

        // scalePixel translate canonical pixel space to pixel space
        // (1920 x 1080, or otherwise indicated in WorldController)
        float scaleCanonicalToActualWidth = Gdx.graphics.getWidth() / (CANONICAL_WIDTH - 2 * PADDING);
        float scaleCanonicalToActualHeight = Gdx.graphics.getHeight() / (CANONICAL_HEIGHT - 2 * PADDING);

        // Take the smaller scale so that we only scale diagonally or something
        // This is for asset scaling
        float finalAssetScale = Math.min(scaleCanonicalToActualWidth, scaleCanonicalToActualHeight);
        // This is for converting things to pixel space?
        float finalPosScale = finalAssetScale * scaleWorldToCanonical;
        scale.set(finalPosScale, finalPosScale);
        actualScale.set(finalAssetScale, finalAssetScale);

        // TODO: Try applying Affine2's to scaling
//        Affine2 worldToCanonical = new Affine2();
//        worldToCanonical.setToScaling(
//                CANONICAL_WIDTH / WORLD_WIDTH,
//                CANONICAL_HEIGHT / WORLD_HEIGHT
//        );
//
//        Affine2 canonicalToActual = new Affine2();
//        canonicalToActual.setToScaling(
//                Gdx.graphics.getWidth() / (CANONICAL_WIDTH - 2 * PADDING),
//                Gdx.graphics.getHeight() / (CANONICAL_HEIGHT - 2 * PADDING)
//        );
//
//        worldToCanonical.applyTo(scale);
//        canonicalToActual.applyTo(scale);
//
//        canonicalToActual.applyTo(actualScale);
    }

    public void drawDecorations(boolean isbrick) {
        for (int i = 0; i < WORLD_WIDTH; i++) {
            for (int j = 0; j < WORLD_HEIGHT; j++) {
                Sprite sprite;
                if (isbrick) {
                    sprite = brick[i][j];
                } else  {
                    sprite = lantern[i][j];
                }

                if (sprite != null) {
                    sprite.draw(GameCanvas.getInstance().getSpriteBatch());
                }
            }
        }
    }

    public void drawBackground() {
        for (int i = 0; i < WORLD_WIDTH; i++) {
            for (int j = 0; j < WORLD_HEIGHT; j++) {
                TextureRegion texture = background[i][j];
                if (texture != null) {
                    GameCanvas.getInstance().draw(
                            texture, Color.WHITE,
                            0, 0,
                            i * getScale().x, j * getScale().y,
                            0, getActualScale().x, getActualScale().y);
                }
            }
        }
    }

    public boolean getOverlapItem(int j) {
        return overlapItem.get(j);
    }

    public void setOverlapItem(int itemId, boolean b) {
        overlapItem.set(itemId, b);
    }
}
