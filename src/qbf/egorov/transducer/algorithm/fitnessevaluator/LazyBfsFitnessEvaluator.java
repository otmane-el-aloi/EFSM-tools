package qbf.egorov.transducer.algorithm.fitnessevaluator;

import qbf.egorov.transducer.algorithm.CanonicalCachedData;
import qbf.egorov.transducer.algorithm.CanonicalInstancesCache;
import qbf.egorov.transducer.algorithm.FST;
import qbf.egorov.transducer.algorithm.FitnessCalculator;

public class LazyBfsFitnessEvaluator extends FitnessEvaluator {
	
	protected CanonicalInstancesCache<CanonicalCachedData> canonicalInstancesCache;

	public LazyBfsFitnessEvaluator(FitnessCalculator fitnessCalculator, int cacheSize) {
		super(fitnessCalculator);
		canonicalInstancesCache = new CanonicalInstancesCache<CanonicalCachedData>(cacheSize);
	}

	@Override
	public double getFitness(FST fst) {
		if (!fst.isFitnessCalculated()) {
			if (fst.needToComputeFitness()) {
				//try looking into the canonicalInstancesCache
				int newId[] = new int[fst.getNumberOfStates()];
				FST canonicalFST = fst.getCanonicalFST(newId);
				if (canonicalInstancesCache.contains(canonicalFST)) {
					CanonicalCachedData data = canonicalInstancesCache.getFirstNonCanonicalInstance(canonicalFST);
					FST cachedInstance = data.getFST();
					double fitness = cachedInstance.fitness();
					if (fitness > 0) {
						fitness += fitnessCalculator.correctFitness(cachedInstance, fst);
					}
					fst.setFitness(fitness);
					
					fst.transformUsedTransitions(data.getNewId(), newId, cachedInstance);
					
					cntBfsSaved++;
				} else {
					numberOfFitnessEvaluations++;
					fst.setFitness(fitnessCalculator.calcFitness(fst));
					fst.setNeedToComputeFitness(false);
					FST copy = fst.copyWithOutputAndMarks();
					copy.setFitness(fst.fitness());
					canonicalInstancesCache.add(new CanonicalCachedData(copy, newId), canonicalFST);
				}
			} else {
				cntLazySaved++;
			}
			fst.setFitnessCalculated(true);
		}
		return fst.fitness();
	}

}
