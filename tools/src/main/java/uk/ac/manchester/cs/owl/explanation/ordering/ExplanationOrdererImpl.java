/* This file is part of the OWL API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright 2014, The University of Manchester
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */
package uk.ac.manchester.cs.owl.explanation.ordering;

import static java.util.stream.Collectors.toList;
import static org.semanticweb.owlapi.util.CollectionFactory.*;
import static org.semanticweb.owlapi.util.OWLAPIPreconditions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAsymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomVisitor;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointDataPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointUnionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentDataPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalDataPropertyAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLHasKeyAxiom;
import org.semanticweb.owlapi.model.OWLInverseFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLIrreflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLPropertyAxiom;
import org.semanticweb.owlapi.model.OWLReflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLRuntimeException;
import org.semanticweb.owlapi.model.OWLSameIndividualAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubDataPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLTransitiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.util.CollectionFactory;

/**
 * Provides ordering and indenting of explanations based on various ordering
 * heuristics.
 * 
 * @author Matthew Horridge, The University Of Manchester, Bio-Health
 *         Informatics Group
 * @since 2.2.0
 */
public class ExplanationOrdererImpl implements ExplanationOrderer {

    private Set<OWLAxiom> currentExplanation;
    @Nonnull
    private final Map<OWLEntity, Set<OWLAxiom>> lhs2AxiomMap = createMap();
    @Nonnull
    private final Map<OWLAxiom, Set<OWLEntity>> entitiesByAxiomRHS = createMap();
    @Nonnull
    private final SeedExtractor seedExtractor = new SeedExtractor();
    @Nonnull
    private final OWLOntologyManager man;
    private OWLOntology ont;
    @Nonnull
    private final Map<OWLObject, Set<OWLAxiom>> mappedAxioms = createMap();
    @Nonnull
    private final Set<OWLAxiom> consumedAxioms = createSet();
    @Nonnull
    private final Set<AxiomType<?>> passTypes = createSet();

    /**
     * Instantiates a new explanation orderer impl.
     * 
     * @param m
     *        the manager to use
     */
    public ExplanationOrdererImpl(@Nonnull OWLOntologyManager m) {
        currentExplanation = Collections.emptySet();
        man = checkNotNull(m, "m cannot be null");
        // I'm not sure what to do with disjoint classes yet. At the
        // moment, we just shove them at the end at the top level.
        passTypes.add(AxiomType.DISJOINT_CLASSES);
    }

    private void reset() {
        lhs2AxiomMap.clear();
        entitiesByAxiomRHS.clear();
        consumedAxioms.clear();
    }

    @Override
    public ExplanationTree getOrderedExplanation(@Nonnull OWLAxiom entailment,
            @Nonnull Set<OWLAxiom> axioms) {
        currentExplanation = new HashSet<>(axioms);
        buildIndices();
        ExplanationTree root = new EntailedAxiomTree(entailment);
        insertChildren(seedExtractor.getSource(entailment), root);
        OWLEntity currentTarget = seedExtractor.getTarget(entailment);
        Set<OWLAxiom> axs = root.getUserObjectClosure();
        List<OWLAxiom> rootAxioms = new ArrayList<>();
        for (OWLAxiom ax : axioms) {
            if (!axs.contains(ax)) {
                rootAxioms.add(ax);
            }
        }
        Set<OWLAxiom> targetAxioms = getTargetAxioms(currentTarget);
        Collections.sort(rootAxioms, (o1, o2) -> {
            if (targetAxioms.contains(o1)) {
                return 1;
            }
            if (targetAxioms.contains(o2)) {
                return -1;
            }
            return 0;
        });
        rootAxioms.forEach(ax -> root.addChild(new ExplanationTree(ax)));
        return root;
    }

    /**
     * Gets the target axioms.
     * 
     * @param target
     *        the current target
     * @return the target axioms
     */
    @Nonnull
    private Set<OWLAxiom> getTargetAxioms(@Nonnull OWLEntity target) {
        Set<OWLAxiom> axioms = new HashSet<>();
        if (target.isOWLClass()) {
            axioms.addAll(ont.axioms(target.asOWLClass()).collect(toList()));
        }
        if (target.isOWLObjectProperty()) {
            axioms.addAll(ont.axioms(target.asOWLObjectProperty()).collect(
                    toList()));
        }
        if (target.isOWLDataProperty()) {
            axioms.addAll(ont.axioms(target.asOWLDataProperty()).collect(
                    toList()));
        }
        if (target.isOWLNamedIndividual()) {
            axioms.addAll(ont.axioms(target.asOWLNamedIndividual()).collect(
                    toList()));
        }
        return axioms;
    }

    @Nonnull
    private Stream<OWLEntity> getRHSEntitiesSorted(@Nonnull OWLAxiom ax) {
        return getRHSEntities(ax).stream().sorted(PROPERTIESFIRST);
    }

    private void insertChildren(@Nonnull OWLEntity entity,
            @Nonnull ExplanationTree tree) {
        Set<OWLAxiom> currentPath = new HashSet<>(
                tree.getUserObjectPathToRoot());
        getAxioms(entity)
                .filter(ax -> !passTypes.contains(ax.getAxiomType()))
                .forEach(
                        ax -> {
                            Set<OWLAxiom> mapped = getIndexedSet(entity,
                                    mappedAxioms, true);
                            if (!consumedAxioms.contains(ax)
                                    && !mapped.contains(ax)
                                    && !currentPath.contains(ax)) {
                                mapped.add(ax);
                                consumedAxioms.add(ax);
                                ExplanationTree child = new ExplanationTree(ax);
                                tree.addChild(child);
                                getRHSEntitiesSorted(ax).forEach(
                                        ent -> insertChildren(ent, child));
                            }
                        });
        sortChildrenAxioms(tree);
    }

    protected Stream<? extends OWLAxiom> getAxioms(OWLEntity entity) {
        if (entity.isOWLClass()) {
            return ont.axioms(entity.asOWLClass());
        }
        if (entity.isOWLObjectProperty()) {
            return ont.axioms(entity.asOWLObjectProperty());
        }
        if (entity.isOWLDataProperty()) {
            return ont.axioms(entity.asOWLDataProperty());
        }
        if (entity.isOWLNamedIndividual()) {
            return ont.axioms(entity.asOWLNamedIndividual());
        }
        return Stream.empty();
    }

    /** The comparator. */
    @Nonnull
    private static final Comparator<Tree<OWLAxiom>> COMPARATOR = (o1, o2) -> {
        OWLAxiom ax1 = o1.getUserObject();
        OWLAxiom ax2 = o2.getUserObject();
        // Equivalent classes axioms always come last
        if (ax1 instanceof OWLEquivalentClassesAxiom) {
            return 1;
        }
        if (ax2 instanceof OWLEquivalentClassesAxiom) {
            return -1;
        }
        if (ax1 instanceof OWLPropertyAxiom) {
            return -1;
        }
        int diff = childDiff(o1, o2);
        if (diff != 0) {
            return diff;
        }
        if (ax1 instanceof OWLSubClassOfAxiom
                && ax2 instanceof OWLSubClassOfAxiom) {
            OWLSubClassOfAxiom sc1 = (OWLSubClassOfAxiom) ax1;
            OWLSubClassOfAxiom sc2 = (OWLSubClassOfAxiom) ax2;
            return sc1.getSuperClass().compareTo(sc2.getSuperClass());
        }
        return 1;
    };

    private static void sortChildrenAxioms(@Nonnull ExplanationTree tree) {
        tree.sortChildren(COMPARATOR);
    }

    private static final AtomicLong RANDOMSTART = new AtomicLong(
            System.currentTimeMillis());

    private void buildIndices() {
        reset();
        AxiomMapBuilder builder = new AxiomMapBuilder();
        currentExplanation.forEach(ax -> ax.accept(builder));
        try {
            if (ont != null) {
                man.removeOntology(verifyNotNull(ont));
            }
            ont = man.createOntology(IRI.create("http://www.semanticweb.org/",
                    "ontology" + RANDOMSTART.incrementAndGet()));
            List<AddAxiom> changes = new ArrayList<>();
            for (OWLAxiom ax : currentExplanation) {
                changes.add(new AddAxiom(verifyNotNull(ont), ax));
                ax.accept(builder);
            }
            man.applyChanges(changes);
        } catch (OWLOntologyCreationException e) {
            throw new OWLRuntimeException(e);
        }
    }

    /**
     * A utility method that obtains a set of axioms that are indexed by some
     * object.
     * 
     * @param <K>
     *        the key type
     * @param <E>
     *        the element type
     * @param obj
     *        The object that indexed the axioms
     * @param map
     *        The map that provides the index structure
     * @param addIfEmpty
     *        A flag that indicates whether an empty set of axiom should be
     *        added to the index if there is not value present for the indexing
     *        object.
     * @return A set of axioms (may be empty)
     */
    @Nonnull
    private static <K, E> Set<E> getIndexedSet(@Nonnull K obj,
            @Nonnull Map<K, Set<E>> map, boolean addIfEmpty) {
        if (addIfEmpty) {
            return map.computeIfAbsent(obj,
                    (x) -> CollectionFactory.<E> createSet());
        }
        Set<E> set = map.get(obj);
        if (set == null) {
            return createSet();
        }
        return set;
    }

    /**
     * Gets axioms that have a LHS corresponding to the specified entity.
     * 
     * @param lhs
     *        The entity that occurs on the left hand side of the axiom.
     * @return A set of axioms that have the specified entity as their left hand
     *         side.
     */
    @Nonnull
    protected Set<OWLAxiom> getAxiomsForLHS(@Nonnull OWLEntity lhs) {
        return getIndexedSet(lhs, lhs2AxiomMap, true);
    }

    /**
     * Gets the rHS entities.
     * 
     * @param axiom
     *        the axiom
     * @return the rHS entities
     */
    @Nonnull
    private Collection<OWLEntity> getRHSEntities(@Nonnull OWLAxiom axiom) {
        return getIndexedSet(axiom, entitiesByAxiomRHS, true);
    }

    /**
     * Index axioms by rhs entities.
     * 
     * @param rhs
     *        the rhs
     * @param axiom
     *        the axiom
     */
    protected void indexAxiomsByRHSEntities(@Nonnull OWLObject rhs,
            @Nonnull OWLAxiom axiom) {
        getIndexedSet(axiom, entitiesByAxiomRHS, true).addAll(
                rhs.signature().collect(toList()));
    }

    /** The properties first comparator. */
    private static final Comparator<OWLObject> PROPERTIESFIRST = (o1, o2) -> {
        if (o1.equals(o2)) {
            return 0;
        }
        if (o1 instanceof OWLProperty && o2 instanceof OWLProperty) {
            return o1.compareTo(o2);
        }
        if (o1 instanceof OWLProperty) {
            return -1;
        }
        return 1;
    };

    private static int childDiff(Tree<OWLAxiom> o1, Tree<OWLAxiom> o2) {
        int childCount1 = o1.getChildCount();
        childCount1 = childCount1 > 0 ? 0 : 1;
        int childCount2 = o2.getChildCount();
        childCount2 = childCount2 > 0 ? 0 : 1;
        return childCount1 - childCount2;
    }

    /** The Class SeedExtractor. */
    private static class SeedExtractor implements OWLAxiomVisitor {

        private OWLEntity source;
        private OWLEntity target;

        SeedExtractor() {}

        /**
         * @param axiom
         *        the axiom
         * @return the source
         */
        @Nonnull
        public OWLEntity getSource(@Nonnull OWLAxiom axiom) {
            axiom.accept(this);
            return verifyNotNull(source);
        }

        /**
         * @param axiom
         *        the axiom
         * @return the target
         */
        @Nonnull
        public OWLEntity getTarget(@Nonnull OWLAxiom axiom) {
            axiom.accept(this);
            return verifyNotNull(target);
        }

        @Override
        public void visit(OWLSubClassOfAxiom axiom) {
            if (!axiom.getSubClass().isAnonymous()) {
                source = axiom.getSubClass().asOWLClass();
            }
            if (!axiom.getSuperClass().isOWLNothing()) {
                OWLClassExpression classExpression = axiom.getSuperClass();
                if (!classExpression.isAnonymous()) {
                    target = classExpression.asOWLClass();
                }
            }
        }

        @Override
        public void visit(OWLDisjointClassesAxiom axiom) {
            axiom.classExpressions().filter(c -> !c.isAnonymous())
                    .forEach(ce -> {
                        if (source == null) {
                            source = ce.asOWLClass();
                        } else if (target == null) {
                            target = ce.asOWLClass();
                        } else {
                            return;
                        }
                    });
        }

        @Override
        public void visit(OWLSubObjectPropertyOfAxiom axiom) {
            if (!axiom.getSubProperty().isAnonymous()) {
                source = axiom.getSubProperty().asOWLObjectProperty();
            }
            if (!axiom.getSuperProperty().isAnonymous()) {
                target = axiom.getSuperProperty().asOWLObjectProperty();
            }
        }

        @Override
        public void visit(OWLClassAssertionAxiom axiom) {
            if (!axiom.getClassExpression().isAnonymous()) {
                source = axiom.getIndividual().asOWLNamedIndividual();
                target = axiom.getClassExpression().asOWLClass();
            }
        }

        @Override
        public void visit(OWLEquivalentClassesAxiom axiom) {
            axiom.namedClasses().forEach(cls -> {
                if (source == null) {
                    source = cls;
                } else if (target == null) {
                    target = cls;
                }
            });
        }

        @Override
        public void visit(SWRLRule rule) {}
    }

    /** A visitor that indexes axioms by their left and right hand sides. */
    private class AxiomMapBuilder implements OWLAxiomVisitor {

        AxiomMapBuilder() {}

        @Override
        public void visit(OWLSubClassOfAxiom axiom) {
            if (!axiom.getSubClass().isAnonymous()) {
                getAxiomsForLHS(axiom.getSubClass().asOWLClass()).add(axiom);
                indexAxiomsByRHSEntities(axiom.getSuperClass(), axiom);
            }
        }

        @Override
        public void visit(OWLAsymmetricObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLReflexiveObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLDisjointClassesAxiom axiom) {
            axiom.classExpressions().forEach(desc -> {
                if (!desc.isAnonymous()) {
                    getAxiomsForLHS(desc.asOWLClass()).add(axiom);
                }
                indexAxiomsByRHSEntities(desc, axiom);
            });
        }

        @Override
        public void visit(OWLDataPropertyDomainAxiom axiom) {
            getAxiomsForLHS(axiom.getProperty().asOWLDataProperty()).add(axiom);
            indexAxiomsByRHSEntities(axiom.getDomain(), axiom);
        }

        @Override
        public void visit(OWLObjectPropertyDomainAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
            indexAxiomsByRHSEntities(axiom.getDomain(), axiom);
        }

        @Override
        public void visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            axiom.properties().forEach(prop -> {
                if (!prop.isAnonymous()) {
                    getAxiomsForLHS(prop.asOWLObjectProperty()).add(axiom);
                }
                indexAxiomsByRHSEntities(prop, axiom);
            });
        }

        @Override
        public void visit(OWLDifferentIndividualsAxiom axiom) {
            axiom.individuals().forEach(ind -> {
                if (!ind.isAnonymous()) {
                    getAxiomsForLHS(ind.asOWLNamedIndividual()).add(axiom);
                    indexAxiomsByRHSEntities(ind, axiom);
                }
            });
        }

        @Override
        public void visit(OWLDisjointDataPropertiesAxiom axiom) {
            axiom.properties().forEach(prop -> {
                getAxiomsForLHS(prop.asOWLDataProperty()).add(axiom);
                indexAxiomsByRHSEntities(prop, axiom);
            });
        }

        @Override
        public void visit(OWLDisjointObjectPropertiesAxiom axiom) {
            axiom.properties().forEach(prop -> {
                if (!prop.isAnonymous()) {
                    getAxiomsForLHS(prop.asOWLObjectProperty()).add(axiom);
                }
                indexAxiomsByRHSEntities(prop, axiom);
            });
        }

        @Override
        public void visit(OWLObjectPropertyRangeAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
            indexAxiomsByRHSEntities(axiom.getRange(), axiom);
        }

        @Override
        public void visit(OWLFunctionalObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLSubObjectPropertyOfAxiom axiom) {
            if (!axiom.getSubProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getSubProperty().asOWLObjectProperty())
                        .add(axiom);
            }
            indexAxiomsByRHSEntities(axiom.getSuperProperty(), axiom);
        }

        @Override
        public void visit(OWLDisjointUnionAxiom axiom) {
            getAxiomsForLHS(axiom.getOWLClass()).add(axiom);
        }

        @Override
        public void visit(OWLSymmetricObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLDataPropertyRangeAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLDataProperty()).add(
                        axiom);
            }
            indexAxiomsByRHSEntities(axiom.getRange(), axiom);
        }

        @Override
        public void visit(OWLFunctionalDataPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLDataProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLEquivalentDataPropertiesAxiom axiom) {
            axiom.properties().forEach(prop -> {
                getAxiomsForLHS(prop.asOWLDataProperty()).add(axiom);
                indexAxiomsByRHSEntities(prop, axiom);
            });
        }

        @Override
        public void visit(OWLClassAssertionAxiom axiom) {
            if (!axiom.getIndividual().isAnonymous()) {
                getAxiomsForLHS(axiom.getIndividual().asOWLNamedIndividual())
                        .add(axiom);
                indexAxiomsByRHSEntities(axiom.getClassExpression(), axiom);
            }
        }

        @Override
        public void visit(OWLEquivalentClassesAxiom axiom) {
            axiom.classExpressions().forEach(desc -> {
                if (!desc.isAnonymous()) {
                    getAxiomsForLHS(desc.asOWLClass()).add(axiom);
                }
                indexAxiomsByRHSEntities(desc, axiom);
            });
        }

        @Override
        public void visit(OWLDataPropertyAssertionAxiom axiom) {
            indexAxiomsByRHSEntities(axiom.getSubject(), axiom);
        }

        @Override
        public void visit(OWLTransitiveObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLSubDataPropertyOfAxiom axiom) {
            getAxiomsForLHS(axiom.getSubProperty().asOWLDataProperty()).add(
                    axiom);
            indexAxiomsByRHSEntities(axiom.getSuperProperty(), axiom);
        }

        @Override
        public void visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
            if (!axiom.getProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getProperty().asOWLObjectProperty()).add(
                        axiom);
            }
        }

        @Override
        public void visit(OWLSameIndividualAxiom axiom) {
            axiom.individuals().filter(ind -> !ind.isAnonymous())
                    .forEach(ind -> {
                        getAxiomsForLHS(ind.asOWLNamedIndividual()).add(axiom);
                        indexAxiomsByRHSEntities(ind, axiom);
                    });
        }

        @Override
        public void visit(OWLInverseObjectPropertiesAxiom axiom) {
            if (!axiom.getFirstProperty().isAnonymous()) {
                getAxiomsForLHS(axiom.getFirstProperty().asOWLObjectProperty())
                        .add(axiom);
            }
            indexAxiomsByRHSEntities(axiom.getFirstProperty(), axiom);
            indexAxiomsByRHSEntities(axiom.getSecondProperty(), axiom);
        }

        @Override
        public void visit(OWLHasKeyAxiom axiom) {
            if (!axiom.getClassExpression().isAnonymous()) {
                indexAxiomsByRHSEntities(axiom.getClassExpression()
                        .asOWLClass(), axiom);
            }
        }
    }
}
