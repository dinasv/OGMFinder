package Core;

import Core.Genomes.WordArray;
import Core.SuffixTrees.*;

import java.util.*;
import Core.Genomes.*;

/**
 * Suffix Tree based algorithm for CSB pattern discovery
 * A CSB is a substring of at least quorum1 input sequences and must have instance in at least quorum2 input sequences
 * An instance can differ from a CSB by at most k insertions
 */
public class MainAlgorithm {

    private static final String DELIMITER = " ";

    public static long countNodesInPatternTree;
    public static long count_nodes_in_data_tree;

    private int maxError;
    private int maxWildcards;
    private int maxDeletion;
    private int maxInsertion;
    private int q1;
    private int q2;
    private int minPatternLength;
    private int maxPatternLength;

    private GeneralizedSuffixTree dataTree;

    //contains all extracted patterns
    private Map<String, Pattern> patterns;

    private boolean multCount;

    private int lastPatternKey;

    private boolean nonDirectons;

    private boolean debug;

    int totalCharsInData;
    GenomesInfo gi;

    /**
     *
     * @param params args
     * @param data_t GST representing all input sequences
     * @param pattern_trie
     * @param gi Information regarding the input genomes (sequences)
     */
    public MainAlgorithm(Parameters params, GeneralizedSuffixTree data_t, Trie pattern_trie, GenomesInfo gi){

        // args
        this.maxError = params.maxError;
        this.maxWildcards = params.maxWildcards;
        this.maxDeletion = params.maxDeletion;
        this.maxInsertion = params.maxInsertion;
        q1 = params.quorum1;
        q2 = params.quorum2;
        this.nonDirectons = params.nonDirectons;
        this.minPatternLength = params.minPatternLength;
        this.maxPatternLength = params.maxPatternLength;
        this.multCount = params.multCount;

        //this.utils = utils;

        dataTree = data_t;

        totalCharsInData = -1;
        this.gi = gi;
        lastPatternKey = 0;

        this.debug = params.debug;

        countNodesInPatternTree = 0;
        count_nodes_in_data_tree = 0;

        patterns = new HashMap<>();

        PatternNode pattern_tree_root;
        if (pattern_trie == null){//all patterns will be extracted from the data tree
            pattern_tree_root = new PatternNode(TreeType.VIRTUAL);
            pattern_tree_root.setKey(++lastPatternKey);
        }else {//if we were given patterns as input
            pattern_tree_root = pattern_trie.getRoot();
        }
        findPatterns(pattern_tree_root);
    }

    public int getPatternsCount(){
        return patterns.size();
    }

    /**
     * Calls the recursive function spellPatterns
     * @param patternNode a node in the pattern tree, the pattern tree traversal begins from this node
     */
    private void findPatterns(PatternNode patternNode) {

        dataTree.computeCount();
        totalCharsInData = ((InstanceNode) dataTree.getRoot()).getCountMultipleInstancesPerGenome();

        InstanceNode dataTreeRoot = (InstanceNode) dataTree.getRoot();
        //the instance of an empty string is the root of the data tree
        Instance empty_instance = new Instance(dataTreeRoot, null, -1, 0, 0);
        count_nodes_in_data_tree ++;

        patternNode.addInstance(empty_instance, maxInsertion);
        if (patternNode.getType()== TreeType.VIRTUAL){
            spellPatternsVirtually(patternNode, dataTreeRoot, -1, null, "",
                    0, 0);
        }else{
            spellPatterns(patternNode, "", 0, 0);
        }
    }


    /**
     * works as the regular substring function
     *
     * @param seq
     * @param start_index
     * @param end_index
     * @return
     */
    private WordArray getSubstring(WordArray seq, int start_index, int end_index) {
        return new WordArray(seq.wordArray, seq.get_start_index() + start_index,
                seq.get_start_index() + end_index);
    }


    /**
     * Remove patterns that are suffixes of existing patterns, and has the same number of instances
     * If a pattern passes the quorum1, all its sub-patterns also pass the quorum1
     * If a (sub-pattern instance count = pattern instance count) : the sub-pattern is always a part of the larger
     * pattern
     * Therefore it is sufficient to remove each pattern suffix if it has the same instance count
     */
    public void removeRedundantPatterns() {
        HashSet<String> patterns_to_remove = new HashSet<>();
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {

            Pattern pattern = entry.getValue();
            String[] pattern_arr = pattern.getPatternArr();

            String[] suffix_arr = Arrays.copyOfRange(pattern_arr, 1, pattern_arr.length);
            String suffix_str = String.join(DELIMITER, suffix_arr) + DELIMITER;
            Pattern suffix = patterns.get(suffix_str);

            if (suffix != null){
                int pattern_count = pattern.getInstanceCount();
                int suffix_count = suffix.getInstanceCount();
                if (suffix_count == pattern_count){
                    patterns_to_remove.add(suffix_str);
                }
            }

            //remove reverse compliments
            if (nonDirectons){
                String pattern_str = String.join(DELIMITER, pattern_arr) + DELIMITER;
                String reversed_pattern_str = String.join(DELIMITER, pattern.getReverseComplimentPatternArr()) + DELIMITER;
                Pattern reversed_pattern = patterns.get(reversed_pattern_str);
                if (reversed_pattern != null && !patterns_to_remove.contains(pattern_str)){
                    patterns_to_remove.add(reversed_pattern_str);
                }
            }
        }
        patterns.keySet().removeAll(patterns_to_remove);
    }

    /**
     * Add to node an edge with label = gap. edge.dest = copy of node, its edged are deep copied
     *
     * @param node
     */
    private void addWildcardEdge(PatternNode node, Boolean copy_node) {
        PatternNode targetNode = node.getTargetNode(gi.WC_CHAR_INDEX);
        if (targetNode == null) {
            int[] wildcard = {gi.WC_CHAR_INDEX};
            //create a copy of node
            PatternNode newnode = new PatternNode(node);
            node.addTargetNode(gi.WC_CHAR_INDEX, newnode);
        } else {
            PatternNode newnode = node.getTargetNode(gi.WC_CHAR_INDEX);
            newnode = new PatternNode(newnode);
            targetNode.addTargetNode(gi.WC_CHAR_INDEX, newnode);
        }
    }

    private Boolean starts_with_wildcard(String pattern) {
        if (pattern.length() > 0) {
            if (pattern.charAt(0) == '*') {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a character to str
     * @param str
     * @param ch the index of the character to add, converted to a letter
     * @return the extended str
     */
    private String appendChar(String str, int ch) {
        String cog = gi.indexToChar.get(ch);
        //System.out.println(cog);
        String extended_string = str + cog + DELIMITER;
        extended_string.intern();
        return extended_string;
    }


    /**
     * Recursive function that traverses over the subtree rooted at pattern_node, which is a node of a suffix tree.
     * This operation 'spells' all possible strings with infix 'pattern', that have enough instances (q1 exact instances
     * and q2 approximate instances)
     *
     * @param pattern_node node in the enumeration tree that represents the current pattern
     * @param pattern     represents a string concatenation of edge labels from root to pattern_node
     * @param pattern_length
     * @param pattern_wildcard_count  number of wildcards in the pattern
     * @return The maximal number of different string indexes that one of the extended patterns by a char appear in
     */
    private int spellPatterns(PatternNode pattern_node, String pattern, int pattern_length, int pattern_wildcard_count) {
        if (pattern_wildcard_count < maxWildcards && pattern_node.getType().equals("enumeration")) {
            //add to pattern_node an edge with "_", pointing to a new node that will save the instances
            addWildcardEdge(pattern_node, true);
        }

        List<Instance> instances = pattern_node.getInstances();

        Map<Integer, PatternNode> target_nodes = pattern_node.getTargetNodes();

        //the maximal number of different instances, of one of the extended patterns
        int max_num_of_diff_instances = -1;
        int num_of_diff_instance = 0;

        PatternNode target_node;
        for (Map.Entry<Integer, PatternNode> entry : target_nodes.entrySet()) {
            int alpha = entry.getKey();
            String alpha_ch = gi.indexToChar.get(alpha);
            target_node = entry.getValue();

            //go over edges that are not wild cards
            if (alpha != gi.WC_CHAR_INDEX) {
                num_of_diff_instance = extendPattern(alpha, -1, null, null,
                                    pattern_wildcard_count, pattern, target_node, pattern_node, instances, pattern_length);

                if (num_of_diff_instance > max_num_of_diff_instances) {
                    max_num_of_diff_instances = num_of_diff_instance;
                }
                //For memory saving, remove pointer to target node
                pattern_node.addTargetNode(alpha, null);
            }
        }

        //handle wild card edge
        if (pattern_node.getType().equals("pattern") || pattern_wildcard_count < maxWildcards) {
            target_node = pattern_node.getTargetNode(gi.WC_CHAR_INDEX);
            if (target_node != null) {
                num_of_diff_instance = extendPattern(gi.WC_CHAR_INDEX, -1, null, null,
                            pattern_wildcard_count + 1, pattern, target_node, pattern_node, instances, pattern_length);
                if (num_of_diff_instance > max_num_of_diff_instances) {
                    max_num_of_diff_instances = num_of_diff_instance;
                }
            }
        }
        countNodesInPatternTree++;

        return max_num_of_diff_instances;
    }


    /**
     * * Recursive function that traverses over the subtree rooted at patternNode, which is a node of a suffix tree.
     * This operation 'spells' all possible strings with infix 'pattern', that have enough instances (q1 exact instances
     * and q2 approximate instances)
     * It is same as spellPatterns, only that the suffix tree of patterns is not saved in memory, it is created virtually
     * from data suffix tree.
     *
     * @param patternNode
     * @param dataNode
     * @param dataEdgeIndex
     * @param dataEdge
     * @param pattern
     * @param patternLength
     * @param wildcardCount
     * @return
     */
    private int spellPatternsVirtually(PatternNode patternNode, InstanceNode dataNode, int dataEdgeIndex,
                                       Edge dataEdge,
                                       String pattern, int patternLength, int wildcardCount) {

        List<Instance> instances = patternNode.getInstances();
        //the maximal number of different instances, of one of the extended patterns
        int maxNumOfDiffInstances = -1;
        int numOfDiffInstances = 0;

        Map<Integer, Edge> dataNodeEdges = null;

        WordArray dataEdgeLabel;
        if (dataEdge != null) {
            dataEdgeLabel = dataEdge.getLabel();
            if (dataEdgeIndex >= dataEdgeLabel.get_length()) {//we reached to the end of the edge
                dataNode = (InstanceNode) dataEdge.getDest();
                dataEdgeIndex = -1;
                dataEdge = null;
            }
        }

        PatternNode targetNode;

        if (dataEdgeIndex == -1){
            dataEdgeIndex ++;
            dataNodeEdges = dataNode.getEdges();

            for (Map.Entry<Integer, Edge> entry : dataNodeEdges.entrySet()) {
                int alpha = entry.getKey();
                String alpha_ch = gi.indexToChar.get(alpha);
                dataEdge = entry.getValue();
                InstanceNode data_tree_target_node = (InstanceNode) dataEdge.getDest();

                if (data_tree_target_node.getCountInstancePerGenome() >= q1) {

                    if (alpha == gi.UNK_CHAR_INDEX) {
                        if (q1 == 0 && !pattern.startsWith("X")) {
                            spellPatternsVirtually(patternNode, dataNode, dataEdgeIndex + 1, dataEdge,
                            pattern, patternLength, wildcardCount);
                        }
                    } else {

                        targetNode = new PatternNode(TreeType.VIRTUAL);
                        targetNode.setKey(++lastPatternKey);

                        numOfDiffInstances = extendPattern(alpha, dataEdgeIndex + 1, dataNode, dataEdge,
                                wildcardCount, pattern, targetNode, patternNode, instances, patternLength);

                        if (numOfDiffInstances > maxNumOfDiffInstances) {
                            maxNumOfDiffInstances = numOfDiffInstances;
                        }
                    }
                }
            }
        }else{//dataEdgeIndex>=1 && dataEdgeIndex < dataEdgeLabel.get_length()
            dataEdgeLabel = dataEdge.getLabel();
            int alpha = dataEdgeLabel.get_index(dataEdgeIndex);

            InstanceNode data_tree_target_node = (InstanceNode) dataEdge.getDest();

            if (data_tree_target_node.getCountInstancePerGenome() >= q1) {
                if (alpha != gi.UNK_CHAR_INDEX) {

                    targetNode = new PatternNode(TreeType.VIRTUAL);
                    targetNode.setKey(++lastPatternKey);

                    numOfDiffInstances = extendPattern(alpha, dataEdgeIndex + 1, dataNode, dataEdge,
                            wildcardCount, pattern, targetNode, patternNode, instances, patternLength);

                    if (numOfDiffInstances > maxNumOfDiffInstances) {
                        maxNumOfDiffInstances = numOfDiffInstances;
                    }
                }
            }
        }

        countNodesInPatternTree++;

        return maxNumOfDiffInstances;
    }

    private void handlePattern(Pattern new_pattern, String extended_pattern){
        patterns.put(extended_pattern, new_pattern);
    }


    /**
     * Extend pattern recursively by one character, if it passes the q1 and q2 - add to pattern list
     *
     * @param alpha                the char to append
     * @param wildcard_count how many wildcard in the pattern
     * @param pattern                previous pattern string, before adding alpha. i.e. COG1234|COG2000|
     * @param targetNode          node the extended pattern
     * @param pattern_node           node of pattern
     * @param Instances            the instances of pattern
     * @param pattern_length
     * @return num of different instances of extended pattern
     */

    private int extendPattern(int alpha, int data_edge_index, InstanceNode data_node, Edge data_edge,
                              int wildcard_count, String pattern, PatternNode targetNode,
                              PatternNode pattern_node, List<Instance> Instances, int pattern_length) {

        String extendedPattern = appendChar(pattern, alpha);
        PatternNode extendedPatternNode = targetNode;
        int extended_pattern_length = pattern_length + 1;

        //if there is a wildcard in the current pattern, have to create a copy of the subtree
        if (wildcard_count > 0 && alpha != gi.WC_CHAR_INDEX) {
            extendedPatternNode = new PatternNode(extendedPatternNode);
            pattern_node.addTargetNode(alpha, extendedPatternNode);
        }

        extendedPatternNode.setSubstring(extendedPattern);
        extendedPatternNode.setSubstringLength(extended_pattern_length);

        int exact_instances_count = 0;
        //go over all instances of the pattern
        for (Instance instance : Instances) {
            int curr_exact_instance_count = extendInstance(extendedPatternNode, instance, alpha);
            if (curr_exact_instance_count > 0){
                exact_instances_count = curr_exact_instance_count;
            }
        }
        extendedPatternNode.setExactInstanceCount(exact_instances_count);

        int diff_instances_count;
        if (multCount){
            diff_instances_count = extendedPatternNode.getInstanceIndexCount();
        }else {
            diff_instances_count = extendedPatternNode.getInstanceKeysSize();
        }

        if (exact_instances_count >= q1 && diff_instances_count >= q2 &&
                (extended_pattern_length - wildcard_count <= maxPatternLength)) {

            TreeType type = extendedPatternNode.getType();
            int ret;
            if (type == TreeType.VIRTUAL){
                ret = spellPatternsVirtually(extendedPatternNode, data_node, data_edge_index, data_edge,
                        extendedPattern, extended_pattern_length, wildcard_count);
            }else {
                ret = spellPatterns(extendedPatternNode, extendedPattern, extended_pattern_length, wildcard_count);
            }

            if (extended_pattern_length - wildcard_count >= minPatternLength) {
                if (type == TreeType.STATIC) {
                    if (extendedPatternNode.getPatternKey()>0) {
                        Pattern newPattern = new Pattern(extendedPatternNode.getPatternKey(), extendedPattern,
                                extendedPattern.split(DELIMITER),
                                extendedPatternNode.getInstanceKeys().size(),
                                extendedPatternNode.getExactInstanceCount());
                        newPattern.addInstanceLocations(extendedPatternNode.getInstances());

                        handlePattern(newPattern, extendedPattern);

                    }
                } else if (type == TreeType.VIRTUAL) {
                    if (alpha != gi.WC_CHAR_INDEX) {
                        if (!(starts_with_wildcard(extendedPattern))) {
                            //make sure that extendedPattern is right maximal, if extendedPattern has the same number of
                            // instances as the longer pattern, prefer the longer pattern
                            if (diff_instances_count > ret || debug) {// diff_instances_count >= ret always
                                Pattern newPattern = new Pattern(extendedPatternNode.getPatternKey(), extendedPattern,
                                        extendedPattern.split(DELIMITER),
                                        extendedPatternNode.getInstanceKeys().size(),
                                        extendedPatternNode.getExactInstanceCount());
                                newPattern.addInstanceLocations(extendedPatternNode.getInstances());

                                handlePattern(newPattern, extendedPattern);

                                if (debug && (getPatternsCount() % 5000 == 0) ){
                                    MemoryUtils.measure();
                                }
                            }
                        }
                    } else {
                        if (ret <= 0) {
                            diff_instances_count = -1;
                        } else {
                            diff_instances_count = ret;
                        }
                    }
                }
            }
        }
        return diff_instances_count;
    }

    /**
     * Extends instance, increments error depending on ch
     *
     * @param extended_pattern extended pattern node
     * @param instance the current instance
     * @param ch  the character of the pattern, need to check if the next char on the instance is equal
     * @return list of all possible extended instances
     */
    private int extendInstance(PatternNode extended_pattern, Instance instance, int ch) {
        //values of current instance
        InstanceNode node_instance = instance.getNodeInstance();
        Edge edge_instance = instance.getEdge();
        int edge_index = instance.getEdgeIndex();
        int error = instance.getError();
        int deletions = instance.getDeletions();
        int insertions = instance.getInsertions();

        //values of the extended instance
        int next_edge_index = edge_index;
        Edge next_edge_instance = edge_instance;
        InstanceNode next_node_instance = node_instance;

        int exact_instance_count = 0;

        //The substring ends at the current node_instance, edge_index = -1
        if (edge_instance == null) {
            //Go over all the edges from node_instance, see if the instance can be extended
            Map<Integer, Edge> instance_edges = node_instance.getEdges();

            //we can extend the instance using all outgoing edges, increment error if needed
            if (ch == gi.WC_CHAR_INDEX) {
                exact_instance_count = addAllInstanceEdges(false, instance, instance_edges, deletions, error, node_instance,
                        edge_index, ch, extended_pattern);
                //extend instance by deletions char
                if (deletions < maxDeletion) {
                    addInstanceToPattern(extended_pattern, instance, gi.GAP_CHAR_INDEX, node_instance, edge_instance, edge_index,
                            error, deletions + 1);
                }
            } else {
                if (insertions < maxInsertion && instance.getLength() > 0){
                    addAllInstanceEdges(true, instance, instance_edges, deletions, error, node_instance,
                            edge_index, ch, extended_pattern);
                }
                if (error < maxError) {
                    //go over all outgoing edges
                    exact_instance_count = addAllInstanceEdges(false, instance, instance_edges, deletions,
                            error, node_instance, edge_index, ch, extended_pattern);
                    //extend instance by deletions char
                    if (deletions < maxDeletion) {
                        addInstanceToPattern(extended_pattern, instance, gi.GAP_CHAR_INDEX, node_instance, edge_instance, edge_index,
                                error, deletions + 1);
                    }
                } else {//error = max error, only edge_instance starting with ch can be added, or deletions
                    next_edge_index++;
                    next_edge_instance = node_instance.getEdge(ch);
                    next_node_instance = node_instance;
                    //Exists an edge_instance starting with ch, add it to instances
                    if (next_edge_instance != null) {
                        exact_instance_count = ((InstanceNode)next_edge_instance.getDest()).getCountInstancePerGenome();
                        //The label contains only 1 char, go to next node_instance
                        if (next_edge_instance.getLabel().get_length() == 1) {
                            next_node_instance = (InstanceNode) next_edge_instance.getDest();
                            next_edge_instance = null;
                            next_edge_index = -1;
                        }
                        addInstanceToPattern(extended_pattern, instance, ch, next_node_instance, next_edge_instance,
                                next_edge_index, error, deletions);
                    } else {
                        //extend instance by deletions char
                        if (deletions < maxDeletion) {
                            addInstanceToPattern(extended_pattern, instance, gi.GAP_CHAR_INDEX, node_instance, edge_instance,
                                    edge_index, error, deletions + 1);
                        }
                    }
                }
            }
        } else {//Edge is not null, the substring ends at the middle of the edge_instance, at index edge_index
            WordArray label = edge_instance.getLabel();
            //check the next char on the label, at edge_index+1
            next_edge_index++;
            int next_ch = label.get_index(next_edge_index);

            //If we reached the end of the label by incrementing edge_index, get next node_instance
            if (next_edge_index == label.get_length() - 1) {
                next_node_instance = (InstanceNode) edge_instance.getDest();
                next_edge_instance = null;
                next_edge_index = -1;
            }

            if (insertions < maxInsertion && instance.getLength() > 0){
                if (next_ch != ch) {
                    String extended_instance_string = appendChar(instance.getSubstring(), next_ch);
                    Instance next_instance = new Instance(next_node_instance, next_edge_instance, next_edge_index,
                            error, deletions, instance.getInsertionIndexes(), extended_instance_string, instance.getLength() + 1);
                    next_instance.addInsertionIndex(instance.getLength());
                    next_instance.addAllInsertionIndexes(instance.getInsertionIndexes());
                    extendInstance(extended_pattern, next_instance, ch);
                    count_nodes_in_data_tree++;
                }
            }

            //if the char is equal add anyway
            if (next_ch == ch) {
                exact_instance_count = ((InstanceNode)edge_instance.getDest()).getCountInstancePerGenome();
                addInstanceToPattern(extended_pattern, instance, next_ch, next_node_instance, next_edge_instance, next_edge_index, error,
                        deletions);
            } else {
                if (ch == gi.WC_CHAR_INDEX) {
                    addInstanceToPattern(extended_pattern, instance, next_ch, next_node_instance, next_edge_instance, next_edge_index, error,
                            deletions);
                } else {
                    if (error < maxError) {//check if the error is not maximal, to add not equal char
                        addInstanceToPattern(extended_pattern, instance, next_ch, next_node_instance, next_edge_instance, next_edge_index,
                                error + 1, deletions);
                    }
                    //extend instance by deletions char
                    if (deletions < maxDeletion) {
                        addInstanceToPattern(extended_pattern, instance, gi.GAP_CHAR_INDEX, node_instance, edge_instance, edge_index, error, deletions + 1);
                    }
                }
            }
        }
        if (error > 0 || deletions > 0 || insertions > 0){
            exact_instance_count = 0;
        }
        return exact_instance_count;
    }

    /**
     * Go over all outgoing edges of instance node
     *
     * @param instance
     * @param instance_edges  edge set of instance_node
     * @param deletions
     * @param error
     * @param instance_node
     * @param edge_index
     * @param ch
     * @param patternNode
     */
    private int addAllInstanceEdges(Boolean make_insertion, Instance instance, Map<Integer, Edge> instance_edges,
                                    int deletions, int error, InstanceNode instance_node, int edge_index, int ch,
                                    PatternNode patternNode) {
        int curr_error = error;
        int next_edge_index;
        int exact_instance_count = 0;

        //boolean exist_equal_char = false;

        //go over all outgoing edges
        for (Map.Entry<Integer, Edge> entry : instance_edges.entrySet()) {
            int next_ch = entry.getKey();
            Edge next_edge = entry.getValue();
            InstanceNode next_node = instance_node;

            if (ch == next_ch) {
                curr_error = error;
                exact_instance_count = ((InstanceNode)next_edge.getDest()).getCountInstancePerGenome();
            } else {
                if (ch != gi.WC_CHAR_INDEX) {//Substitution - the chars are different, increment error
                    curr_error = error + 1;
                }
            }

            //The label contains only 1 char, go to next instance_node
            if (next_edge.getLabel().get_length() == 1) {
                next_node = (InstanceNode) next_edge.getDest();
                next_edge = null;
                next_edge_index = -1;
            } else {//label contains more the 1 char, increment edge_index
                next_edge_index = edge_index + 1;
            }

            if (make_insertion) {
                if (ch != next_ch) {
                    String extended_instance_string = appendChar(instance.getSubstring(), next_ch);
                    Instance next_instance = new Instance(next_node, next_edge, next_edge_index, error, deletions,
                            instance.getInsertionIndexes(), extended_instance_string, instance.getLength() + 1);
                    next_instance.addInsertionIndex(instance.getLength());
                    extendInstance(patternNode, next_instance, ch);
                    count_nodes_in_data_tree++;
                }
            } else {
                addInstanceToPattern(patternNode, instance, next_ch, next_node, next_edge, next_edge_index, curr_error, deletions);
            }
        }
        return exact_instance_count;
    }

    /**
     * @param extended_pattern
     * @param instance
     * @param next_ch
     * @param next_node
     * @param next_edge
     * @param next_edge_index
     * @param next_error
     * @param next_deletions
     * @throws Exception
     */
    private void addInstanceToPattern(PatternNode extended_pattern, Instance instance, int next_ch, InstanceNode next_node,
                                      Edge next_edge, int next_edge_index, int next_error, int next_deletions) {

        String extended_instance_string = appendChar(instance.getSubstring(), next_ch);
        Instance next_instance = new Instance(next_node, next_edge, next_edge_index, next_error, next_deletions,
                instance.getInsertionIndexes(), extended_instance_string, instance.getLength()+1);
        extended_pattern.addInstance(next_instance, maxInsertion);

        count_nodes_in_data_tree++;
    }


    /**
     * @return
     */
    public List<Pattern> getPatterns() {
        return new ArrayList<Pattern>(patterns.values());
    }


}
