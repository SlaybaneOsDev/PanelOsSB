// • ▌ ▄ ·.  ▄▄▄·  ▄▄ • ▪   ▄▄· ▄▄▄▄·  ▄▄▄·  ▐▄▄▄  ▄▄▄ .
// ·██ ▐███▪▐█ ▀█ ▐█ ▀ ▪██ ▐█ ▌▪▐█ ▀█▪▐█ ▀█ •█▌ ▐█▐▌·
// ▐█ ▌▐▌▐█·▄█▀▀█ ▄█ ▀█▄▐█·██ ▄▄▐█▀▀█▄▄█▀▀█ ▐█▐ ▐▌▐▀▀▀
// ██ ██▌▐█▌▐█ ▪▐▌▐█▄▪▐█▐█▌▐███▌██▄▪▐█▐█ ▪▐▌██▐ █▌▐█▄▄▌
// ▀▀  █▪▀▀▀ ▀  ▀ ·▀▀▀▀ ▀▀▀·▀▀▀ ·▀▀▀▀  ▀  ▀ ▀▀  █▪ ▀▀▀
//      Magicbane Emulator Project © 2013 - 2022
//                www.magicbane.com





// • ▌ ▄ ·.  ▄▄▄·  ▄▄ • ▪   ▄▄· ▄▄▄▄·  ▄▄▄·  ▐▄▄▄  ▄▄▄ .
// ·██ ▐███▪▐█ ▀█ ▐█ ▀ ▪██ ▐█ ▌▪▐█ ▀█▪▐█ ▀█ •█▌ ▐█▐▌·
// ▐█ ▌▐▌▐█·▄█▀▀█ ▄█ ▀█▄▐█·██ ▄▄▐█▀▀█▄▄█▀▀█ ▐█▐ ▐▌▐▀▀▀
// ██ ██▌▐█▌▐█ ▪▐▌▐█▄▪▐█▐█▌▐███▌██▄▪▐█▐█ ▪▐▌██▐ █▌▐█▄▄▌
// ▀▀  █▪▀▀▀ ▀  ▀ ·▀▀▀▀ ▀▀▀·▀▀▀ ·▀▀▀▀  ▀  ▀ ▀▀  █▪ ▀▀▀
//      Magicbane Emulator Project © 2013 - 2022
//                www.magicbane.com


package engine.objects;

import engine.Enum;
import engine.InterestManagement.WorldGrid;
import engine.db.archive.DataWarehouse;
import engine.db.archive.MineRecord;
import engine.gameManager.*;
import engine.net.ByteBufferWriter;
import engine.net.client.msg.ErrorPopupMsg;
import engine.server.MBServerStatics;
import engine.session.SessionID;
import org.pmw.tinylog.Logger;

import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static engine.gameManager.DbManager.MineQueries;
import static engine.gameManager.DbManager.getObject;
import static engine.math.FastMath.sqr;

public class Mine extends AbstractGameObject {

	private String zoneName;
	private Resource production;
	private boolean isActive = false;
	private float latitude;
	private float longitude;
	private float altitude;
	private Guild owningGuild;
	private int lastClaimerID;
	private SessionID lastClaimerSessionID;
	private int flags;
	private int buildingID;
	private Zone parentZone;
	private MineProduction mineType;
	public LocalDateTime openDate;

	public boolean dirtyMine = false;
	//flags 1: never been claimed (make active).





	// Not persisted to DB
	private String guildName;
	private GuildTag guildTag;
	private String nationName;
	private GuildTag nationTag;
	public static ConcurrentHashMap<Mine, Integer> mineMap = new ConcurrentHashMap<>(MBServerStatics.CHM_INIT_CAP, MBServerStatics.CHM_LOAD, MBServerStatics.CHM_THREAD_LOW);
	public static ConcurrentHashMap<Integer, Mine> towerMap = new ConcurrentHashMap<>(MBServerStatics.CHM_INIT_CAP, MBServerStatics.CHM_LOAD, MBServerStatics.CHM_THREAD_LOW);

	private static long lastChange = System.currentTimeMillis();
	public static LocalDateTime effectiveMineDate;

	/**
	 * ResultSet Constructor
	 */
	public Mine(ResultSet rs) throws SQLException, UnknownHostException {
		super(rs);

		this.mineType = MineProduction.getByName(rs.getString("mine_type"));

		float offsetX = rs.getFloat("mine_offsetX");
		float offsetZ = rs.getFloat("mine_offsetZ");
		int ownerUID = rs.getInt("mine_ownerUID");
		this.buildingID = rs.getInt("mine_buildingUID");
		this.flags = rs.getInt("flags");
		int parent = rs.getInt("parent");
		this.parentZone = ZoneManager.getZoneByUUID(parent);
		if (parentZone != null) {
			this.latitude = parentZone.getLoc().x + offsetX;
			this.longitude = parentZone.getLoc().z + offsetZ;
			this.altitude = parentZone.getLoc().y;
			if (this.parentZone.getParent() != null)
				this.zoneName = this.parentZone.getParent().getName();
			else
				this.zoneName = this.parentZone.getName();
		} else {
			Logger.error( "Missing parentZone of ID " + parent);
			this.latitude = -1000;
			this.longitude = 1000;
			this.altitude = 0;
			this.zoneName = "Unknown Mine";
		}
		


		this.owningGuild = Guild.getGuild(ownerUID);
		Guild nation = null;
		if (this.owningGuild != null && !this.owningGuild.isErrant()) {
			this.guildName = this.owningGuild.getName();
			this.guildTag = this.owningGuild.getGuildTag();
			nation = this.owningGuild.getNation();
		} else {
			this.guildName = "";
			this.guildTag = GuildTag.ERRANT;
			nation = Guild.getErrantGuild();
			this.owningGuild = Guild.getErrantGuild();
		}
		
		int mineTime = this.owningGuild.getMineTime();

		if(!nation.isErrant()) {
			this.nationName = nation.getName();
			this.nationTag = nation.getGuildTag();
			mineTime = nation.getMineTime();
		} else {
			this.nationName = "";
			this.nationTag = GuildTag.ERRANT;
			
		}
		this.setActive(false);
		this.production = Resource.valueOf(rs.getString("mine_resource"));

		this.lastClaimerID = 0;
		this.lastClaimerSessionID = null;

	java.sql.Timestamp mineTimeStamp = rs.getTimestamp("mine_openDate");
	
	
	Building building = BuildingManager.getBuildingFromCache(this.buildingID);
	

	if (mineTimeStamp == null && (this.owningGuild == null || this.owningGuild.isErrant() || building.getRank() < 1)){
		if (building != null){
			String zoneName = building.getParentZone().getName();
			String parentZoneName = building.getParentZone().getParent().getName();
			Logger.info(zoneName + " in " + parentZoneName + " has a dirty mine, setting active.");
		}
		this.dirtyMine = true;
		openDate = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
		return;
	}else if (this.owningGuild.isErrant() || nation.isErrant()){
		openDate = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
		return;
	}else if (mineTimeStamp == null){
	
		this.openDate = LocalDateTime.now().withHour(mineTime).withMinute(0).withSecond(0).withNano(0);
		
		if (LocalDateTime.now().isAfter(this.openDate.plusHours(1)))
			this.openDate = this.openDate.plusDays(1);
		return;
	}else{
		this.openDate =  mineTimeStamp.toLocalDateTime().withHour(mineTime);
		
		if (LocalDateTime.now().isAfter(this.openDate.plusHours(1))){
			this.openDate = this.openDate.plusDays(1);
			return;
		}
	}
	
	//after 1 day...
	if(this.openDate.getDayOfYear() - LocalDateTime.now().getDayOfYear() > 1){
		this.openDate = this.openDate.withDayOfYear(LocalDateTime.now().getDayOfYear());
		if (LocalDateTime.now().isAfter(this.openDate.plusHours(1)))
			this.openDate = this.openDate.plusDays(1);
		return;
	}
	
	}

    public static void SendMineAttackMessage(Building mine){

        if (mine.getBlueprint() == null)
            return;

        if (mine.getBlueprint().getBuildingGroup() != Enum.BuildingGroup.MINE)
            return;

        
        if (mine.getGuild().isErrant())
        	return;
        
        if (mine.getGuild().getNation().isErrant())
            return;

        if (mine.getTimeStamp("MineAttack") > System.currentTimeMillis())
            return;

        mine.getTimestamps().put("MineAttack", System.currentTimeMillis() + MBServerStatics.ONE_MINUTE);

        ChatManager.chatNationInfo(mine.getGuild().getNation(), mine.getName() + " in " + mine.getParentZone().getParent().getName() + " is Under attack!");
    }

    private void setNextMineWindow() {
    	
    	//default time, 9 pm est
    	int mineHour = 21;
    	
    	if (this.owningGuild != null || this.owningGuild.isErrant() == false)
    		mineHour = this.owningGuild.getNation().getMineTime();
    	int days = 1;
    	
    	//midnight hours
    	if (this.openDate.getHour() != 0 && this.openDate.getHour() != 24 && (this.openDate.getDayOfMonth() == LocalDateTime.now().getDayOfMonth()))
    		if (mineHour == 0 || mineHour == 24)
    			days = 2;
    	
    	LocalDateTime newTime = this.openDate.plusDays(days).withHour(mineHour).withMinute(0).withSecond(0).withNano(0);
    	
    	DbManager.MineQueries.CHANGE_MINE_TIME(this, newTime);
		this.openDate = newTime;
	}

	public static void loadAllMines() {

		// Set current mine effective date
try{
	

		effectiveMineDate = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

		//Load mine resources
		MineProduction.addResources();

		//pre-load all building sets
		ArrayList<Mine> serverMines = MineQueries.GET_ALL_MINES_FOR_SERVER();

		for (Mine mine : serverMines) {
			Mine.mineMap.put(mine, mine.buildingID);
			Mine.towerMap.put(mine.buildingID, mine);
			mine.initializeMineTime();
		}
		
}catch (Exception e){
	e.printStackTrace();
}
	}

	/*
	 * Getters
	 */
	private void initializeMineTime(){

		//Mine time has already been set at loading from the database. skip.

		if (this.openDate != null)
			return;

		Guild nation = null;

		if (this.owningGuild != null)
			nation = this.owningGuild.getNation();

		int mineTime = (nation != null && !nation.isErrant()) ? nation.getMineTime() : MBServerStatics.MINE_EARLY_WINDOW;

		LocalDateTime openDate = LocalDateTime.now().withHour(mineTime).withMinute(0).withSecond(0).withNano(0);

		//Failed to Update Database, default mine time.

		if (!MineQueries.CHANGE_MINE_TIME(this, openDate)){
			Logger.info("Mine with UUID " + this.getObjectUUID() + " failed to set Mine Window. Defaulting to Earliest.");
			openDate = openDate.withHour(MBServerStatics.MINE_EARLY_WINDOW).withMinute(0).withSecond(0).withNano(0);
			this.openDate = openDate;
			return;
		}

		this.openDate = openDate;

	}

	public boolean changeProductionType(Resource resource){
		if (!this.validForMine(resource))
			return false;
		//update resource in database;
		if(!MineQueries.CHANGE_RESOURCE(this, resource))
			return false;

		this.production = resource;
		return true;
	}

	public MineProduction getMineType() {
		return this.mineType;
	}

	public String getZoneName() {
		return this.zoneName;
	}

	public Resource getProduction() {
		return this.production;
	}

	public boolean getIsActive() {
		return this.isActive;
	}

	public float getAltitude() {
		return this.altitude;
	}

	public Guild getOwningGuild() {
		return this.owningGuild;
	}

	public int getFlags() {
		return flags;
	}

	public void setFlags(int flags) {
		this.flags = flags;
	}

	public Zone getParentZone() {
		return parentZone;
	}

	public GuildTag getGuildTag() {
		return guildTag;
	}

	public void setMineType(String type) {
		this.mineType = MineProduction.getByName(type);
	}

	public void setActive(boolean isAc) {

		this.isActive = isAc;
		Building building = BuildingManager.getBuildingFromCache(this.buildingID);
		if (building != null && !this.isActive)
			building.isDeranking.compareAndSet(true, false);
	}

	public void setOwningGuild(Guild owningGuild) {
		this.owningGuild = owningGuild;
	}

	public static Mine getMineFromTower(int towerID) {
		return Mine.towerMap.get(towerID);
	}

	public boolean validForMine(Resource r) {
		if (this.mineType == null)
			return false;
		return this.mineType.validForMine(r, this.isExpansion());
	}

	/*
	 * Serialization
	 */
	
	public static void serializeForClientMsg(Mine mine,ByteBufferWriter writer) {
		writer.putInt(mine.getObjectType().ordinal());
		writer.putInt(mine.getObjectUUID());
		writer.putInt(mine.getObjectUUID()); //actually a hash of mine
		//		writer.putInt(0x215C92BB); //this.unknown1);
		writer.putString(mine.mineType.name);
		writer.putString(mine.zoneName);
		writer.putInt(mine.production.hash);
		writer.putInt(mine.production.baseProduction);
		writer.putInt(mine.getModifiedProductionAmount()); //TODO calculate range penalty here
		writer.putInt(3600); //window in seconds

		LocalDateTime mw = mine.openDate;

		writer.putLocalDateTime(mw);
		mw = mw.plusHours(1);
		writer.putLocalDateTime(mw);
		writer.put(mine.isActive ? (byte) 0x01 : (byte) 0x00);

		writer.putFloat(mine.latitude);
		writer.putFloat(mine.altitude);
		writer.putFloat(mine.longitude);
		writer.putInt(mine.isExpansion() ? mine.mineType.xpacHash : mine.mineType.hash);

		writer.putString(mine.guildName);
		GuildTag._serializeForDisplay(mine.guildTag,writer);
		writer.putString(mine.nationName);
		GuildTag._serializeForDisplay(mine.nationTag,writer);
	}

	public void serializeForMineProduction(ByteBufferWriter writer) {
		writer.putInt(this.getObjectType().ordinal());
		writer.putInt(this.getObjectUUID());
		writer.putInt(this.getObjectUUID()); //actually a hash of mine
		//		writer.putInt(0x215C92BB); //this.unknown1);
		writer.putString(this.mineType.name);
		writer.putString(this.zoneName);
		writer.putInt(this.production.hash);
		writer.putInt(this.production.baseProduction);
		writer.putInt(this.getModifiedProductionAmount()); //TODO calculate range penalty here
		writer.putInt(3600); //window in seconds
		writer.putInt(this.isExpansion() ? this.mineType.xpacHash : this.mineType.hash);
	}

	public static ArrayList<Mine> getMinesForGuild(int guildID) {
		ArrayList<Mine> mineList = new ArrayList<>();
		for (Mine mine : Mine.mineMap.keySet()) {
			if (mine.owningGuild != null && mine.owningGuild.getObjectUUID() == guildID)
				mineList.add(mine);
		}
		return mineList;
	}

	public static long getLastChange() {
		return lastChange;
	}

	public static void setLastChange(long lastChange) {
		Mine.lastChange = lastChange;
	}

	/*
	 * Database
	 */
	public static Mine getMine(int UID){
        return MineQueries.GET_MINE(UID);

	}

	public static ArrayList<Mine> getMines() {
		return new ArrayList<>(mineMap.keySet());
	}

	@Override
	public void updateDatabase() {
		// TODO Create update logic.
	}

	public int getBuildingID() {
		return buildingID;
	}

	public void setBuildingID(int buildingID) {
		this.buildingID = buildingID;
	}

	public void handleStartMineWindow() {

		// Do not open errant mines until after woo

		//		if  ((this.getOwningGuild() == null) &&
		//				(this.getOpenDate().isAfter(DateTime.now())))
		//			return;

		this.lastClaimerID = 0;
		this.setActive(true);
		ChatManager.chatSystemChannel(this.zoneName + "'s Mine is now Active!");
		Logger.info(this.zoneName + "'s Mine is now Active!");
	}

	public static boolean validClaimer(PlayerCharacter pc) {
		//verify the player exists
		if (pc == null)
			return false;

		//verify the player is in valid guild
		Guild g = pc.getGuild();
		if (g == null) {
			ChatManager.chatSystemError(pc, "Mine can only be claimed by a guild.");
			return false;
		} else if (g.isErrant()) {
			ChatManager.chatSystemError(pc, "Guild cannot be Errant to claim..");
			return false;
		}

		//verify the player is in nation
		Guild n = g.getNation();
		if (n.isErrant()) {
			ChatManager.chatSystemError(pc, "Must have a Nation");
			return false;
		}

		
		if (SessionManager.getPlayerCharacterByID(pc.getObjectUUID()) == null){
			return false;
		}
		//Get a count of nation mines, can't go over capital tol rank.
		City capital = n.getOwnedCity();
		City guildCity = g.getOwnedCity();
		if (guildCity == null){
			ChatManager.chatSystemError(pc, "Guild must own city to claim.");
			return false;
		}
		if (capital == null) {
			ChatManager.chatSystemError(pc, "Guild must own city to claim.");
			return false;
		}

		if (guildCity.getWarehouse() == null){
			ChatManager.chatSystemError(pc, "City must own warehouse for to claim.");
			return false;
		}

		Building tol = capital.getTOL();

		if (tol == null) {
			ChatManager.chatSystemError(pc, "Tree of life not found for city.");
			return false;
		}
		
		int rank = tol.getRank();

		if (rank < 1) {
			ChatManager.chatSystemError(pc, "Tree of life is not yet sprouted.");
			return false;
		}

		int mineCnt = 0;

		mineCnt += Mine.getMinesForGuild(n.getObjectUUID()).size();
		for (Guild guild: n.getSubGuildList()){
			mineCnt += Mine.getMinesForGuild(guild.getObjectUUID()).size();
		}


		if (mineCnt > rank) {
			ChatManager.chatSystemError(pc, "Your Nation can only hold " + tol.getRank() + " mines. Your Nation already has" + mineCnt);
			return false;
		}

		return true;
	}


	public void handleDestroyMine() {

		if (!this.isActive)
			return;

		//remove tags from mine

		this.guildName = "";
		this.nationName = "";
		this.owningGuild = null;
		Mine.setLastChange(System.currentTimeMillis());

		// remove hirelings

		Building building = (Building) getObject(Enum.GameObjectType.Building, this.buildingID);
		BuildingManager.cleanupHirelings(building);
	}

	public boolean handleEndMineWindow(){

		Building mineBuilding = BuildingManager.getBuildingFromCache(this.buildingID);

		if (mineBuilding == null){
			Logger.debug( "Failed to Activate Mine with UID " + this.getObjectUUID() +". Unable to Load Building with UID " +this.buildingID);
			return false;
		}

		if (mineBuilding.getRank() > 0) {
			//never knocked down, let's just move on.
			//hasn't been claimed since server start.
			if (this.dirtyMine && this.lastClaimerID == 0 && (this.owningGuild == null || this.owningGuild.isErrant()))
				return false;
			this.setActive(false);
			setNextMineWindow();
			return true;
		}

		PlayerCharacter claimer = PlayerCharacter.getFromCache(this.lastClaimerID);

		if (!validClaimer(claimer)){
			LocalDateTime resetTime = LocalDateTime.now().withDayOfMonth(LocalDateTime.now().getDayOfMonth()).withHour(LocalDateTime.now().getHour()).withMinute(0).withSecond(0).withNano(0);
			this.openDate = resetTime;
			return false;
		}
			

		//		//verify the player hasn't logged out since claim

		//		if (SessionManager.getSession(claimer) == null)
		//			return false;
		//		if (!SessionManager.getSession(claimer).getSessionID().equals(this.lastClaimerSessionID))
		//			return false;

		if (this.owningGuild == null || this.owningGuild.isErrant() || this.owningGuild.getNation().isErrant()){
			LocalDateTime resetTime = LocalDateTime.now().withDayOfMonth(LocalDateTime.now().getDayOfMonth()).withHour(LocalDateTime.now().getHour()).withMinute(0).withSecond(0).withNano(0);
			this.openDate = resetTime;
			return false;
		}
	

		//Update ownership to map

		this.guildName = this.owningGuild.getName();
		this.guildTag = this.owningGuild.getGuildTag();
		Guild nation = this.owningGuild.getNation();
		this.nationName = nation.getName();
		this.nationTag = nation.getGuildTag();
		
		LocalDateTime guildDate = LocalDateTime.now().withHour(this.owningGuild.getMineTime()).withMinute(0).withSecond(0).withNano(0);
		
		if (this.openDate.getDayOfMonth() == LocalDateTime.now().getDayOfMonth())
		if (this.owningGuild.getMineTime() == 0 || this.owningGuild.getMineTime() == 24)
			guildDate = guildDate.plusDays(1);
		
		guildDate = guildDate.withHour(this.owningGuild.getMineTime()).withMinute(0).withSecond(0).withNano(0);
		this.openDate = guildDate;
		Mine.setLastChange(System.currentTimeMillis());

		if (mineBuilding.getRank() < 1){

			if (claimer == null){
				this.lastClaimerID = 0;
				updateGuildOwner(null);
				return false;
			}
			
			this.dirtyMine = false;

			mineBuilding.rebuildMine();
			WorldGrid.updateObject(mineBuilding);
			ChatManager.chatSystemChannel(claimer.getName() + " has claimed the mine in " + this.parentZone.getParent().getName() + " for " + this.owningGuild.getName() + ". The mine is no longer active.");

			// Warehouse this claim event

			MineRecord mineRecord = MineRecord.borrow(this, claimer, Enum.RecordEventType.CAPTURE);
			DataWarehouse.pushToWarehouse(mineRecord);

		}else{
			mineBuilding.setRank(mineBuilding.getRank());
		}

		this.setActive(false);
		setNextMineWindow();
		return true;
	}

	public boolean claimMine(PlayerCharacter claimer){

		if (claimer == null)
			return false;

		if (!validClaimer(claimer))
			return false;

		if (!this.isActive) {
			ErrorPopupMsg.sendErrorMsg(claimer, "Can not for to claim inactive mine.");
			return false;
		}

		if (!updateGuildOwner(claimer))
			return false;

		this.lastClaimerID = claimer.getObjectUUID();
		Mine.setLastChange(System.currentTimeMillis());
		return true;
	}
	public boolean depositMineResources(){

		if (this.owningGuild == null)
			return false;

		if (this.owningGuild.getOwnedCity() == null)
			return false;

		if (this.owningGuild.getOwnedCity().getWarehouse() == null)
			return false;

		ItemBase resourceIB = ItemBase.getItemBase(this.production.UUID);
		return this.owningGuild.getOwnedCity().getWarehouse().depositFromMine(this,resourceIB, this.getModifiedProductionAmount());
	}

	public boolean updateGuildOwner(PlayerCharacter pc){

		Building mineBuilding = BuildingManager.getBuildingFromCache(this.buildingID);

		//should never return null, but let's check just in case.

		if (mineBuilding == null){
			ChatManager.chatSystemError(pc, "Unable to find mine tower.");
			Logger.debug("Failed to Update Mine with UID " + this.getObjectUUID() +". Unable to Load Building with UID " +this.buildingID );
			return false;
		}

		if (pc == null) {
			this.owningGuild = null;
			this.guildName = "None";
			this.guildTag = GuildTag.ERRANT;
			this.nationName = "None";
			this.nationTag = GuildTag.ERRANT;
			//Update Building.
			mineBuilding.setOwner(null);
			WorldGrid.updateObject(mineBuilding);
			return true;
		}

		if (SessionManager.getSession(pc) != null) {
			this.lastClaimerSessionID = SessionManager.getSession(pc).getSessionID();
		} else {
			Logger.error("Failed to find session for player " + pc.getObjectUUID());

			return false;
		}
		
		Guild guild = pc.getGuild();

		if (guild.getOwnedCity() == null)
			return false;

		if (!MineQueries.CHANGE_OWNER(this, guild.getObjectUUID())) {
			Logger.debug("Database failed to Change Ownership of Mine with UID " + this.getObjectUUID());
			ChatManager.chatSystemError(pc, "Failed to claim Mine.");
			return false;
		}


		//All tests passed.

		//update mine.
		this.owningGuild = guild;
		//		this.guildName = this.owningGuild.getName();
		//		this.guildTag = this.owningGuild.getGuildTag();
		//
		//		//nation will never return null, read getNation()
		//		Guild nation = this.owningGuild.getNation();
		//		this.nationName = nation.getName();
		//		this.nationTag = nation.getGuildTag();

		//Update Building.
		PlayerCharacter guildLeader = (PlayerCharacter) Guild.GetGL(this.owningGuild);
		if (guildLeader != null)
			mineBuilding.setOwner(guildLeader);
		WorldGrid.updateObject(mineBuilding);
		return true;
	}

	public boolean isExpansion(){
        return (this.flags & 2) != 0;
    }

	public int getModifiedProductionAmount(){
		//TODO Calculate Distance modifications.

		//calculate base values.
		int baseProduction = this.production.baseProduction;
		float baseModValue = this.production.baseProduction * .1f;
		float rankModValue = this.production.baseProduction * .0143f;
		float totalModded = 0;

		//get Mine Building.
		Building mineBuilding = BuildingManager.getBuilding(this.buildingID);
		if (mineBuilding == null)
			return this.production.baseProduction;
		for (AbstractCharacter harvester:mineBuilding.getHirelings().keySet()){
			totalModded += baseModValue;
			totalModded += rankModValue * harvester.getRank();
		}
		//add base production on top;
		totalModded += baseProduction;
		//skip distance check for expansion.
		if (this.isExpansion())
			return (int) totalModded;

		if (this.owningGuild != null){
			if(this.owningGuild.getOwnedCity() != null){
				float distanceSquared = this.owningGuild.getOwnedCity().getLoc().distanceSquared2D(mineBuilding.getLoc());

				if (distanceSquared > sqr(10000 * 3))
					totalModded *=.25f;
				else if (distanceSquared > sqr(10000 * 2))
					totalModded *= .50f;
				else if (distanceSquared > sqr(10000))
					totalModded *= .75f;
			}
		}
		return (int) totalModded;

	}

}
