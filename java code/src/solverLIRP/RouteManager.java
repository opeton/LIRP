
/* test */

package solverLIRP;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import instanceManager.Instance;
import tools.JSONParser;
import tools.Parameters;

public class RouteManager {

	/*=====================
	 *      ATTRIBUTES
	 ======================*/
	private Instance instLIRP;													// The instance to which the routes apply
	private HashMap<Integer, HashMap<Integer, LinkedHashSet<Route>>> routes; 	// A route is referenced with its level (0 : supplier-depot, 1 : depot-client)
	// For a given level, routes are ordered according to their number of stops
	private int[] nbRoutesLvl;													// Total number of routes at each level (0: Supplier to depots, 1: Depots to clients)

	/**
	 * Create a RouteManager object from an instance and the type of model under investigation
	 * @param instLIRP	the instance from which the set of routes is created
	 */
	public RouteManager(Instance instLIRP) throws IOException {
		/* Create direct routes for the instance */
		this.instLIRP = instLIRP;
		this.routes = new HashMap<Integer, HashMap<Integer, LinkedHashSet<Route>>>();

		/* Create HashMaps to store the sets of routes at each level */
		this.nbRoutesLvl = new int[Parameters.nb_levels];
		for(int lvl = 0; lvl < Parameters.nb_levels; lvl++) {
			this.routes.put(lvl, new HashMap<Integer, LinkedHashSet<Route>>());
			this.nbRoutesLvl[lvl] = 0;
		}
	}

	/**
	 * Create a RouteManager object from an instance and the type of model under investigation
	 * @param instLIRP		The instance from which the set of routes is created
	 * @param onlyDirect	If we only want to generate direct routes and modify instLIRP so that all clients are reachable
	 * @throws IOException
	 */
	public void initialize(boolean onlyDirect) throws IOException {
		this.populateDirect();
		if(!onlyDirect) {
			/* Populate loops at both levels */
			for(int lvl = 0; lvl < Parameters.nb_levels; lvl++) {
				this.populateLoops(lvl, 1);
				this.populateLoops(lvl, 1);
			}
		}
	}

	/*
	 * ACCESSORS
	 */
	/**
	 * 
	 * @return	the instance to which the routes in the RouteManager object relate
	 */
	public Instance getInstance() {
		return this.instLIRP;
	}

	/**
	 * 
	 * @return	the ArrayList of direct routes from the supplier to the depots
	 */
	public LinkedHashSet<Route> getAllRoutesOfType(int lvl, int nbStops) {
		return this.routes.get(lvl).get(nbStops);
	}

	/**
	 * 
	 * @return	the ArrayList of multi-stops routes from the supplier to the depots
	 */
	public int getNbRoutesOfType(int lvl, int nbStops){
		return this.routes.get(lvl).get(nbStops).size();
	}

	/*
	 * METHODS
	 */
	/**
	 * Fill the direct routes arrays with all the valid direct routes for the instance considered. 
	 * If a location is not reachable from an upper level, re-position this location on the map.
	 * @param instLIRP		the LIRP instance
	 * @throws IOException
	 */
	private void populateDirect() throws IOException {
		/* Loop through the different levels */
		for(int lvl = 0; lvl < Parameters.nb_levels; lvl++) {
			/* Create a LinkedHashSet to store the feasible routes on the current level as they are computed */
			LinkedHashSet<Route> directLvl = new LinkedHashSet<Route>();
			int sIndex = 0;
			/* Fill the direct routes first and test that each location of the level is reachable from at least one site on the upper level */
			while(sIndex < this.instLIRP.getNbDepots(lvl)) {
				int nbUpperSites = this.instLIRP.getNbDepots(lvl - 1);
				int uIndex = (lvl == 0) ? -1 : 0;
				boolean reachable = false;
				while(uIndex < nbUpperSites) {
					/* Create a new direct route from the current upper site (uIndex) to the current lower site (sIndex) */
					Route directR = new Route(this.instLIRP, uIndex, sIndex);
					/* If its duration is lower than the maximum time allowed, add it to the list and mark the lower site as reachable*/
					if(directR.isValid()) {
						reachable = true;
						directLvl.add(directR);
						this.nbRoutesLvl[lvl] += 1;
					}
					uIndex++;
				}
				/* If the site is reachable, go to the next site index */
				if(reachable) {
					sIndex++;
				}
				/* Otherwise, re-position the site randomly and check if the new location is reachable from an upper site */
				else {
					if(lvl == Parameters.nb_levels - 1)
						this.instLIRP.drawClient(sIndex);
					else
						this.instLIRP.drawDepot(lvl, sIndex);
				}
			}
			/* Add the direct routes to the corresponding level in the HashMap */
			this.routes.get(lvl).put(1, directLvl);
		}
	}

	/**
	 * Fill the loop routes HashSets at a specific level
	 * @param lvl			the level at which we want to create the loop routes
	 * @param nbStops		the number of stops of the starting routes to extend
	 * @throws IOException
	 */
	private void populateLoops(int lvl, int nbStops) throws IOException {
		/* HashSet to store the loop routes with nbStops+1 stops at level lvl */
		LinkedHashSet<Route> loopsLvl = new LinkedHashSet<Route>();

		/* Fill an array list with the potential stop candidates to add to the loop routes */
		int finalIndex = 0;
		if(lvl == Parameters.nb_levels - 1)
			finalIndex = this.instLIRP.getNbClients();
		else
			finalIndex = this.instLIRP.getNbDepots(lvl);

		/* If the stopping time is small enough, build the multi-stops routes for this instance */
		/* Start from each of the existing direct routes */
		for(Route startRoute : this.routes.get(lvl).get(nbStops)) {
			/* If it is possible to add a stop */
			if(startRoute.getDuration() + Parameters.stopping_time < Parameters.max_time_route) {
				/* Start at the maximum last possible stop */
				int stopToAdd = finalIndex - 1;
				int maxStop = startRoute.getMaxStop();
				while(stopToAdd > maxStop) {
					/* Create a new route candidate by adding the stop to the current route */
					Route routeCandidate = startRoute.extend(stopToAdd);
					/* Add it to the set of possible routes if it is valid */
					if(routeCandidate.isValid()) {
						loopsLvl.add(routeCandidate);
						this.nbRoutesLvl[lvl] += 1;
					}
					stopToAdd--;
				}
			}
		}
		/* If some routes have been found for this number of stops, add it to the HashMap */
		if(!loopsLvl.isEmpty()) {
			this.routes.get(lvl).put(nbStops + 1, loopsLvl);
			populateLoops(lvl, nbStops + 1);
		}
	}


	//	/**
	//	 * 
	//	 * @param Allocation  allocation matrix
	//	 * @return list of selected depot indices
	//	 * @throws IOException
	//	 */
	//	private ArrayList<Integer> getListOfSelectedDepots(int[][] Allocation)throws IOException 
	//	{
	//		ArrayList<Integer> S = new ArrayList<Integer>();
	//		int nd = this.instLIRP.getNbDepots(0); 
	//		int nc = this.instLIRP.getNbClients(); 
	//		
	//		// If depot d has at least one client allocated to it, we consider it is selected, otherwise not. 
	//		for (int d=0; d<nd;d++){
	//			for (int c=0; c<nc;c++) {
	//				if (Allocation[c][d] ==1)
	//					S.add(d);
	//				c=nc; // if at least one client is allocated to depot d, depot d exists and we can check next depot
	//			}
	//		}
	//		return S;
	//	}
	//	
	//	/**
	//	 * @param : d : depot index
	//	 * @param Allocation  allocation matrix
	//	 * @return list of clients allocated to d
	//	 * @throws IOException
	//	 * **/
	//	 
	//		private ArrayList<Integer> getListOfAllocatedClients(int d, int[][] Allocation)throws IOException 
	//		{
	//			ArrayList<Integer> AAA = new ArrayList<Integer>();
	//			int nc = this.instLIRP.getNbClients(); 
	//			
	//			for (int c=0; c<nc;c++) {
	//					if (Allocation[c][d] == 1) AAA.add(c);
	//			}
	//			return AAA;
	//		}
	//	 
	//	
	//	
	//	
	//	/**
	//	 * 
	//	 * @param loopSD        routes from the supplier to depots
	//	 * @param Allocation    matrix with customer allocation. Depot with 0 client = not selected
	//	 * @return list of SD routes: SD routes starting from an unselected depot are filtered
	//	 * @throws IOException
	//	 */
	//
	//	// GUILLAUME
	//	//private HashMap<Integer, HashMap<Integer, LinkedHashSet<Route>>> filterSDRoutes(ArrayList<Route> loopSD, int[][] Allocation) throws IOException {
	//
	//	// OLIVIER
	//	ArrayList<Route> filterSDRoutes(ArrayList<Route> loopSD, int[][] Allocation) throws IOException {
	//		
	//		ArrayList<Route> filteredSD = new ArrayList<Route>();
	//		ArrayList<Integer> S =  getListOfSelectedDepots(Allocation);
	//	
	//		for (int d=0; d<S.size();d++){
	//			Route r = new Route(this.instLIRP, -1, S.get(d)); // create a new SD route r from s to d
	//			filteredSD.add(r);
	//		}
	//		return filteredSD;
	//	}
	//
	//
	//	/**
	//	 * 
	//	 * @param loopDC        routes from depots to clients
	//	 * @param Allocation    matrix with customer allocation. 
	//	 * @return filtered list of DC routes. 
	//	 * DC route starting from an unselected depot are filtered
	//	 * @throws IOException
	//	 */
	//
	//		// OLIVIER
	//	private ArrayList<Route> filterDCRoutes(ArrayList<Route> loopDC, int[][] Allocation) throws IOException {
	//
	//		int keep; // indicates if a route must be kept or not in the filtered list
	//		ArrayList<Route> filteredDC = new ArrayList<Route>();
	//		ArrayList<Integer> S =  getListOfSelectedDepots(Allocation);
	//		
	//		for (int itr=0; itr<loopDC.size();itr++) {
	//			Route r = loopDC.get(itr);
	//			instanceManager.Location rdep = r.getStart();
	//			// Ici ca risque de ne pas fonctionner car rdep est de type Location et S continet des entiers (a v�rifier)
	//			if (S.contains(rdep)){
	//					filteredDC.add(r);
	//			}
	//		}
	//		return filteredDC;
	//	}
	//	
	//	
	//	
	//	
	//	/**
	//	 * 
	//	 * @param loopDC        routes from depots to clients
	//	 * @param Allocation    matrix with customer allocation. 
	//	 * @return filtered list of DC routes. 
	//	 * DC Routes where all clients not preAllocated to d are filtered
	//	 * @throws IOException
	//	 */
	//
	//	// GUILLAUME
	//	//private LinkedHashSet<Route> filterRoutes(ArrayList<Route> loopDC, int[][] Allocation) throws IOException {
	//
	//	// OLIVIER
	//	private ArrayList<Route> filterRoutes(ArrayList<Route> loopDC, int[][] Allocation) throws IOException {
	//
	//		int keep; // indicates if a route must be kept or not in the filtered list
	//		ArrayList<Integer> S =  getListOfSelectedDepots(Allocation);
	//		ArrayList<Route> filtered = new ArrayList<Route>();
	//
	//		for (int itr=0; itr<loopDC.size();itr++) {
	//			keep=1;
	//			Route r = loopDC.get(itr);
	//			ArrayList<Integer> AAA = getListOfAllocatedClients(itr, Allocation);
	//			if (r.containsAll(AAA)) { // all clients are allocated to the depot of route r
	//				filtered.add(r);
	//			}
	//		}
	//		return filtered;
	//	}



	//			/* Build additional routes from every existing shorter route */
	//			for(Route startRouteLvl0 : this.routes.get(0).get(nbStops - 1)) {
	//				for(int stopIter = 0; stopIter < stopCandidates.size(); stopIter++) {
	//					/* Create a new Route object by adding one stop among the candidates to currentRoute */
	//					Route routeCandidate = currentRoute.extend(stopCandidates.get(stopIter));
	//					/* If it is valid, add it to the set of routes to add and call recursively */
	//					if(routeCandidate.isValid()) {
	//			}
	//			/* Fill a list with depots candidates for insertion in loops */
	//			ArrayList<Integer> stopDCandidates = new ArrayList<Integer>();
	//			for(int dIter = 0; dIter < this.instLIRP.getNbDepots(0); dIter++) {
	//				stopDCandidates.add(dIter);
	//			}
	//			/* Create loops with the different possible combinations of stops */
	//			for(int dIndex = 0; dIndex < stopDCandidates.size() - 1; dIndex++) {
	//				Route initSDRoute = new Route(this.instLIRP, -1, dIndex);
	//				loopsLvl0.addAll(computeAllRoutes(initSDRoute, new ArrayList<Integer>(stopDCandidates.subList(dIndex + 1, stopDCandidates.size())), maxNbStops - 1));
	//			}
	//
	//			/* Fill a list with depots candidates for insertion in loops */
	//			ArrayList<Integer> stopCCandidates = new ArrayList<Integer>();
	//			for(int cIter = 0; cIter < this.instLIRP.getNbClients(); cIter++) {
	//				stopCCandidates.add(cIter);
	//			}
	//			/* Create loops starting from each depot */
	//			for(int dIter = 0; dIter < this.instLIRP.getNbDepots(0); dIter++) {
	//				/* Routes from the depot dIter are useful only if the depot is reachable from the supplier */
	//				/* Create loops with the different possible combinations of stops */
	//				for(int cIndex = 0; cIndex < stopCCandidates.size() - 1; cIndex++) {
	//					Route initDCRoute = new Route(this.instLIRP, dIter, cIndex);
	//					loopsLvl1.addAll(computeAllRoutes(initDCRoute, new ArrayList<Integer>(stopCCandidates.subList(cIndex + 1, stopCCandidates.size())), maxNbStops - 1));
	//				}
	//			}
	//		}
	//	}

	//
	//	/**
	//	 * 
	//	 * @param currentRoute		the current Route object that is considered as a basis to build new ones
	//	 * @param stopCandidates		the stops that can be added to currentRoute
	//	 * @param nbRemainingStops	the maximum number of stops that can be added to the currentRoute
	//	 * @return
	//	 * @throws IOException
	//	 */
	//	private ArrayList<Route> computeAllRoutes(Route currentRoute, ArrayList<Integer> stopCandidates, int nbRemainingStops) throws IOException {
	//		ArrayList<Route> routesToAdd = new ArrayList<Route>();
	//		/* If some stop candidates remain to extend the route, try to add them to the route */
	//		if(nbRemainingStops > 0 && !stopCandidates.isEmpty()) {
	//			for(int stopIter = 0; stopIter < stopCandidates.size(); stopIter++) {
	//				/* Create a new Route object by adding one stop among the candidates to currentRoute */
	//				Route routeCandidate = currentRoute.extend(stopCandidates.get(stopIter));
	//				/* If it is valid, add it to the set of routes to add and call recursively */
	//				if(routeCandidate.isValid()) {
	//					routesToAdd.add(routeCandidate);
	//					/* If the stop currently added is not the last of the list, call recursively with the remaining candidates */
	//					if(stopIter < stopCandidates.size() - 1) {
	//						ArrayList<Integer> newStopCandidates = new ArrayList<Integer>(stopCandidates.subList(stopIter + 1, stopCandidates.size()));
	//						routesToAdd.addAll(computeAllRoutes(routeCandidate, newStopCandidates, nbRemainingStops - 1));
	//					}
	//				}
	//			}
	//		}
	//		return routesToAdd;
	//	}

	/**
	 * 
	 * @return	a JSON object containing the RouteManager object
	 * @throws IOException
	 */
	public JSONObject getJSONRM() throws IOException {
		// Create a JSON Object to describe the depots map
		JSONObject jsonRM = new JSONObject();
		jsonRM.put("instance id", this.instLIRP.getID());

		JSONObject jsonLvls = new JSONObject();
		for(int lvl = 0; lvl < Parameters.nb_levels; lvl++) {
			//JSONArray jsonLvlRoutes = new JSONArray();
			Iterator<Map.Entry<Integer, LinkedHashSet<Route>>> mapRouteIter = this.routes.get(lvl).entrySet().iterator();
			JSONObject jsonLvlRoutes = new JSONObject();
			while (mapRouteIter.hasNext()) {
				Map.Entry<Integer, LinkedHashSet<Route>> setPair = mapRouteIter.next();
				JSONArray jsonRouteArray = new JSONArray();
				Iterator<Route> routesIter = setPair.getValue().iterator();
				while(routesIter.hasNext()) {
					jsonRouteArray.put(routesIter.next().getJSONRoute());
				}
				jsonLvlRoutes.put(setPair.getKey().toString(), jsonRouteArray);
			}
			jsonLvls.put(String.valueOf(lvl), jsonLvlRoutes);

		}
		jsonRM.put("routes", jsonLvls);

		return jsonRM;
	}

	/**
	 * Write the JSON object of this map to a file
	 * @param filename	the destination file
	 * @throws IOException
	 */
	public void writeToJSONFile(String filename) throws IOException {
		System.out.println(filename);
		JSONParser.writeJSONToFile(this.getJSONRM(), filename);
	}
}
