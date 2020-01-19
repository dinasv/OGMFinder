package model.genomes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 */
public class Directon implements GenomicSegment {

    private int id;
    private int repliconId;
    private int genomeId;

    protected List<Gene> genes;
    private Strand strand;
    private int startIndex;

    public Directon(int id, int repliconId, int genomeId){
        this.id = id;
        this.repliconId = repliconId;
        this.genomeId = genomeId;

        genes = new ArrayList<>();
        strand = Strand.INVALID;
        startIndex = 0;
    }

    public void removeUnkChars(String UNK_CHAR){
        removeXFromEnd(UNK_CHAR);
        removeXFromStart(UNK_CHAR);
    }

    private void removeXFromEnd(String UNK_CHAR){
        int i = size()-1;
        while (i>=0 && genes.get(i).getCogId().equals(UNK_CHAR)){
            i--;
        }
        if (i != size()-1) {
            genes = new ArrayList<Gene>(genes.subList(0, i + 1));
        }
    }

    private void removeXFromStart(String UNK_CHAR){
        int i = 0;
        while (i<size() && genes.get(i).getCogId().equals(UNK_CHAR)){
            i++;
        }
        if (i != 0) {
            genes = new ArrayList<Gene>(genes.subList(2, genes.size()));
        }
    }

    public void setStrand(Strand strand) {
        this.strand = strand;
    }

    @Override
    public int getStartIndex() {
        return startIndex;
    }

    @Override
    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    @Override
    public List<Gene> getGenes() {
        return genes;
    }

    @Override
    public int getRepliconId() {
        return repliconId;
    }

    @Override
    public int getGenomeId() {
        return genomeId;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public int size() {
        return genes.size();
    }

    public void addGene(Gene gene){
        if(getStrand() == Strand.REVERSE) {
            genes.add(0, gene);
        }else{
            genes.add(gene);
        }
    }

    @Override
    public void addAllGenes(List<Gene> genes) {
        this.genes.addAll(genes);
    }

    @Override
    public void addAllGenes(Gene[] genes) {
        addAllGenes(Arrays.asList(genes));
    }

    @Override
    public Strand getStrand() {
        return strand;
    }

}
